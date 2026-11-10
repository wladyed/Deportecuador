package ec.deportecuador.web.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminWorkspaceController {
    private final JdbcTemplate db;
    private final AdminAccounts accounts;
    private final CrmService crm;
    public AdminWorkspaceController(JdbcTemplate db, AdminAccounts accounts, CrmService crm) { this.db=db; this.accounts=accounts; this.crm=crm; }
    public record Invite(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=180) String name, @NotNull AdminIdentity.Role role) {}
    public record Permissions(@NotNull AdminIdentity.Role role, boolean enabled) {}
    public record Assignment(UUID userId) {}
    public record RequestView(UUID id, String type, String organization, String country, String name,
                              String email, String phone, String description, List<String> interests,
                              String locale, String status, Instant createdAt, UUID assignedTo, String assignedName) {}
    public record RequestPage(List<RequestView> items, long total, int page, int size) {}
    public record Summary(String type, String status, long count) {}
    public record AuditView(UUID id, UUID actorId, String action, UUID targetId, Instant createdAt) {}

    @GetMapping("/users") public List<AdminAccounts.UserView> users(@AuthenticationPrincipal AdminIdentity actor) {
        AdminAccounts.requireAdmin(actor); return accounts.users();
    }
    @PostMapping("/users") @ResponseStatus(HttpStatus.CREATED)
    public AdminAccounts.Invitation invite(@AuthenticationPrincipal AdminIdentity actor, @Valid @RequestBody Invite input) {
        return accounts.invite(actor,input.email(),input.name(),input.role());
    }
    @PatchMapping("/users/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void permissions(@AuthenticationPrincipal AdminIdentity actor, @PathVariable UUID id, @Valid @RequestBody Permissions input) {
        accounts.updateUser(actor,id,input.role(),input.enabled());
    }
    @PostMapping("/users/{id}/invitation")
    public AdminAccounts.Invitation renew(@AuthenticationPrincipal AdminIdentity actor, @PathVariable UUID id) {
        return accounts.renewInvitation(actor,id);
    }

    @GetMapping("/requests") public RequestPage requests(@AuthenticationPrincipal AdminIdentity actor,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
            @RequestParam(defaultValue="") String type, @RequestParam(defaultValue="") String status,
            @RequestParam(defaultValue="false") boolean due) {
        if (actor == null || (actor.role()!=AdminIdentity.Role.ADMIN && actor.role()!=AdminIdentity.Role.COMMERCIAL))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (page < 0 || page > 100000 || size < 1 || size > 100 || !List.of("","DEMO","ADVICE").contains(type))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        String where = " WHERE 1=1"; List<Object> params = new ArrayList<>();
        if (actor.role()==AdminIdentity.Role.COMMERCIAL) { where += " AND r.assigned_to=?"; params.add(actor.id()); }
        if (!type.isEmpty()) { where += " AND r.request_type=?"; params.add(type); }
        if (!status.isEmpty()) {
            if(!Set.of("NEW","CONTACTED","PROPOSAL","WON","LOST").contains(status))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            where += " AND r.status=?"; params.add(status);
        }
        if(due)where += " AND r.next_contact_at<=CURRENT_TIMESTAMP AND r.status NOT IN ('WON','LOST')";
        long total = db.queryForObject("SELECT count(*) FROM commercial_request r"+where, Long.class, params.toArray());
        params.add(size); params.add(page*size);
        List<RequestView> items = db.query("SELECT r.*,u.display_name AS assigned_name FROM commercial_request r LEFT JOIN admin_user u ON u.id=r.assigned_to"+where+" ORDER BY r.created_at DESC,r.id LIMIT ? OFFSET ?", (rs,n) ->
            new RequestView(rs.getObject("id",UUID.class),rs.getString("request_type"),rs.getString("organization"),rs.getString("country"),rs.getString("contact_name"),rs.getString("email"),rs.getString("phone"),rs.getString("description"),List.of(rs.getString("interests").split(",")),rs.getString("locale"),rs.getString("status"),rs.getTimestamp("created_at").toInstant(),rs.getObject("assigned_to",UUID.class),rs.getString("assigned_name")), params.toArray());
        return new RequestPage(items,total,page,size);
    }

    @PatchMapping("/requests/{id}/assignment") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    public void assign(@AuthenticationPrincipal AdminIdentity actor, @PathVariable UUID id, @RequestBody Assignment assignment) {
        crm.assign(actor,id,assignment.userId());
    }
    @GetMapping("/reports") public List<Summary> reports() {
        return db.query("SELECT request_type,status,count(*) AS total FROM commercial_request GROUP BY request_type,status ORDER BY request_type,status",
            (rs,n) -> new Summary(rs.getString("request_type"),rs.getString("status"),rs.getLong("total")));
    }
    @GetMapping("/audit") public List<AuditView> audit() {
        return db.query("SELECT * FROM admin_audit ORDER BY created_at DESC LIMIT 100", (rs,n) -> new AuditView(rs.getObject("id",UUID.class),rs.getObject("actor_id",UUID.class),rs.getString("action"),rs.getObject("target_id",UUID.class),rs.getTimestamp("created_at").toInstant()));
    }
}
