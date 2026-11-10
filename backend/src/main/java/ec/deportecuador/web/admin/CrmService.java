package ec.deportecuador.web.admin;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;

@Service
public class CrmService {
    private final JdbcTemplate db;
    private final AdminAccounts accounts;
    public CrmService(JdbcTemplate db, AdminAccounts accounts) { this.db=db; this.accounts=accounts; }
    public record Workflow(String status,long version,Instant nextContactAt,String closeReason,Instant closedAt) {}
    public record Activity(UUID id,String actor,String kind,String body,Instant createdAt) {}
    public record Detail(Workflow workflow,List<Activity> activities) {}
    public record Notice(UUID id,UUID requestId,String kind,Instant createdAt,Instant readAt) {}
    static Timestamp stamp(Instant value) { return value==null?null:Timestamp.from(value); }
    static Instant instant(Timestamp value) { return value==null?null:value.toInstant(); }
    private Map<String,Object> locked(AdminIdentity actor, UUID id) {
        if(actor==null || (actor.role()!=AdminIdentity.Role.ADMIN && actor.role()!=AdminIdentity.Role.COMMERCIAL))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var rows=db.queryForList("SELECT * FROM commercial_request WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty() || (actor.role()==AdminIdentity.Role.COMMERCIAL && !actor.id().equals(rows.get(0).get("assigned_to"))))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return rows.get(0);
    }
    private Workflow workflow(UUID id) {
        return db.queryForObject("SELECT * FROM commercial_request WHERE id=?",(r,n)->new Workflow(r.getString("status"),r.getLong("crm_version"),instant(r.getTimestamp("next_contact_at")),r.getString("close_reason"),instant(r.getTimestamp("closed_at"))),id);
    }
    @Transactional public Detail detail(AdminIdentity actor,UUID id,int page) {
        if(page<0 || page>100000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        locked(actor,id);
        var items=db.query("SELECT a.*,u.display_name FROM crm_activity a JOIN admin_user u ON u.id=a.actor_id WHERE request_id=? ORDER BY a.created_at DESC,a.id LIMIT 50 OFFSET ?",(r,n)->new Activity(r.getObject("id",UUID.class),r.getString("display_name"),r.getString("kind"),r.getString("body"),r.getTimestamp("created_at").toInstant()),id,page*50);
        return new Detail(workflow(id),items);
    }
    @Transactional public void update(AdminIdentity actor,UUID id,long version,String status,Instant next,String reason) {
        locked(actor,id); var old=workflow(id);
        if(old.version()!=version)throw new ResponseStatusException(HttpStatus.CONFLICT,"La solicitud cambió. Vuelve a abrir el detalle antes de guardar.");
        boolean closed=Set.of("WON","LOST").contains(status);
        boolean wasClosed=Set.of("WON","LOST").contains(old.status());
        boolean allowed=old.status().equals(status) || switch(old.status()) {
            case "NEW" -> Set.of("CONTACTED","LOST").contains(status);
            case "CONTACTED" -> Set.of("PROPOSAL","WON","LOST").contains(status);
            case "PROPOSAL" -> Set.of("CONTACTED","WON","LOST").contains(status);
            case "WON","LOST" -> actor.role()==AdminIdentity.Role.ADMIN && status.equals("CONTACTED");
            default -> false;
        };
        if(!allowed || (wasClosed && closed))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Transición de estado no permitida; un Administrador puede reabrir como Contactada.");
        reason=reason==null?"":reason.trim();
        if(closed && reason.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Indica el motivo de cierre.");
        if(next!=null && !next.isAfter(Instant.now()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El próximo contacto debe ser futuro.");
        if(closed)next=null;
        if(!closed && status.equals(old.status()) && Objects.equals(next,old.nextContactAt()))return;
        db.update("UPDATE commercial_request SET status=?,next_contact_at=?,close_reason=?,closed_at=?,crm_version=crm_version+1,followup_alerted=FALSE WHERE id=?",status,stamp(next),closed?reason:null,closed?stamp(Instant.now()):null,id);
        db.update("UPDATE crm_notice SET cancelled=TRUE WHERE request_id=? AND kind='FOLLOWUP'",id);
        if(closed)db.update("UPDATE crm_notice SET cancelled=TRUE WHERE request_id=? AND sent_at IS NULL",id);
        activity(actor,id,"WORKFLOW",old.status()+" → "+status+". Próximo contacto: "+(next==null?"sin programar":next)+ (closed?". Motivo: "+reason:""));
        accounts.audit(actor.id(),"REQUEST_WORKFLOW",id);
    }
    @Transactional public void note(AdminIdentity actor,UUID id,String kind,String body) {
        locked(actor,id);
        if(!Set.of("CALL","MEETING","EMAIL","NOTE").contains(kind) || body.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        activity(actor,id,kind,body.trim());accounts.audit(actor.id(),"REQUEST_ACTIVITY",id);
    }
    private void activity(AdminIdentity actor,UUID id,String kind,String body) {
        db.update("INSERT INTO crm_activity(id,request_id,actor_id,kind,body,created_at) VALUES(?,?,?,?,?,CURRENT_TIMESTAMP)",UUID.randomUUID(),id,actor.id(),kind,body);
    }
    @Transactional public void assign(AdminIdentity actor,UUID id,UUID assignee) {
        AdminAccounts.requireAdmin(actor);var row=locked(actor,id);
        if(Objects.equals(row.get("assigned_to"),assignee))return;
        String label="Sin asignar";
        if(assignee!=null) {
            var target=accounts.identity(assignee).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST));
            if(target.role()!=AdminIdentity.Role.ADMIN && target.role()!=AdminIdentity.Role.COMMERCIAL)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            label=target.name();
        }
        db.update("UPDATE commercial_request SET assigned_to=?,crm_version=crm_version+1,followup_alerted=FALSE WHERE id=?",assignee,id);
        db.update("UPDATE crm_notice SET cancelled=TRUE WHERE request_id=?",id);
        activity(actor,id,"ASSIGNMENT","Responsable: "+label);accounts.audit(actor.id(),"REQUEST_ASSIGNED",id);
        if(assignee!=null && !Set.of("WON","LOST").contains((String)row.get("status")))notice(id,assignee,"ASSIGNMENT");
    }
    void notice(UUID request,UUID recipient,String kind) {
        db.update("INSERT INTO crm_notice(id,request_id,recipient_id,kind,created_at,next_attempt_at) VALUES(?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",UUID.randomUUID(),request,recipient,kind);
    }
    @Transactional public void scheduleDue() {
        var rows=db.queryForList("SELECT r.id,r.assigned_to FROM commercial_request r JOIN admin_user u ON u.id=r.assigned_to WHERE r.next_contact_at<=CURRENT_TIMESTAMP AND r.followup_alerted=FALSE AND r.status NOT IN ('WON','LOST') AND u.enabled=TRUE AND u.role IN ('ADMIN','COMMERCIAL') FOR UPDATE OF r");
        for(var row:rows) {
            UUID id=(UUID)row.get("id");notice(id,(UUID)row.get("assigned_to"),"FOLLOWUP");
            db.update("UPDATE commercial_request SET followup_alerted=TRUE WHERE id=?",id);
        }
    }
    public List<Notice> notices(AdminIdentity actor) {
        return db.query("SELECT n.* FROM crm_notice n JOIN commercial_request r ON r.id=n.request_id WHERE n.recipient_id=? AND r.assigned_to=? AND n.cancelled=FALSE ORDER BY n.created_at DESC LIMIT 100",(r,n)->new Notice(r.getObject("id",UUID.class),r.getObject("request_id",UUID.class),r.getString("kind"),r.getTimestamp("created_at").toInstant(),instant(r.getTimestamp("read_at"))),actor.id(),actor.id());
    }
    public void read(AdminIdentity actor,UUID id) {
        db.update("UPDATE crm_notice SET read_at=CURRENT_TIMESTAMP WHERE id=? AND recipient_id=?",id,actor.id());
    }
}
