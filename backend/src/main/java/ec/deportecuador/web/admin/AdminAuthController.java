package ec.deportecuador.web.admin;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {
    private final AdminAccounts accounts; private final GoogleIdentity google;
    private final SecurityContextRepository contexts;
    private final CsrfTokenRepository csrf;
    public AdminAuthController(AdminAccounts accounts, SecurityContextRepository contexts, CsrfTokenRepository csrf, GoogleIdentity google) {
        this.google=google; this.accounts=accounts; this.contexts=contexts; this.csrf=csrf;
    }
    public record Login(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=72) String password) {}
    public record Code(@NotBlank @Pattern(regexp="[0-9]{6}|[a-fA-F0-9]{32}") String code) {}
    public record Enrollment(@NotBlank @Size(max=128) String token, @NotBlank @Email @Size(max=254) String email,
                             @NotBlank @Size(max=180) String name, @NotBlank @Size(min=12,max=72) String password, boolean bootstrap) {}
    @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }
    @GetMapping("/status") public Map<String,Boolean> status(HttpServletRequest request) {var session=request.getSession(false);boolean pending=session!=null&&session.getAttribute("admin.pending")!=null&&session.getAttribute("admin.expires") instanceof Instant expires&&expires.isAfter(Instant.now());return Map.of("setupRequired", accounts.setupRequired(),"mfaRequired",pending,"googleEnabled",google.enabled()); }
    @GetMapping("/me") public AdminIdentity me(@AuthenticationPrincipal AdminIdentity actor) { return actor; }

    @PostMapping("/login") public Map<String,Boolean> login(@Valid @RequestBody Login input, HttpServletRequest request) {
        var proof = accounts.checkPasswordProof(input.email(), input.password());
        if (proof == null) throw unauthorized();
        UUID id=proof.id();
        var session = request.getSession(); request.changeSessionId();
        session.removeAttribute("admin.enrollment"); session.removeAttribute("admin.attempts");
        session.setAttribute("admin.pending", id);
        session.setAttribute("admin.pendingVersion", proof.version());
        session.setAttribute("admin.expires", Instant.now().plusSeconds(300));
        return Map.of("mfaRequired", true);
    }
    @PostMapping("/enroll") public Map<String,String> enroll(@Valid @RequestBody Enrollment input, HttpServletRequest request) {
        var pending = accounts.beginEnrollment(input.token(),input.email(),input.name(),input.password(),input.bootstrap());
        var session = request.getSession(); request.changeSessionId();
        session.removeAttribute("admin.pending"); session.removeAttribute("admin.attempts");
        session.setAttribute("admin.enrollment", pending);
        return Map.of("secret", pending.secret(), "account", pending.email(), "issuer", "DeportEcuador");
    }
    @PostMapping("/mfa") public AdminIdentity mfa(@Valid @RequestBody Code input, HttpServletRequest request, HttpServletResponse response) {
        var session = request.getSession(false);
        if (session == null) throw unauthorized();
        Integer attempts = (Integer)session.getAttribute("admin.attempts");
        int count = attempts == null ? 1 : attempts + 1;
        session.setAttribute("admin.attempts", count);
        if (count > 5) { session.invalidate(); throw unauthorized(); }
        AdminIdentity actor = null;long authenticatedVersion=0;
        var enrollment = (AdminAccounts.Pending)session.getAttribute("admin.enrollment");
        if (enrollment != null) actor = accounts.finishEnrollment(enrollment, input.code());
        else {
            UUID id = (UUID)session.getAttribute("admin.pending");
            Instant expires = (Instant)session.getAttribute("admin.expires");
            Long version=(Long)session.getAttribute("admin.pendingVersion");
            if (id != null && version!=null && expires != null && expires.isAfter(Instant.now())) {authenticatedVersion=version;actor = accounts.checkFactorVersion(id, input.code(),version);}
        }
        if (actor == null) throw unauthorized();
        session.removeAttribute("admin.pending"); session.removeAttribute("admin.expires");
        session.removeAttribute("admin.enrollment"); session.removeAttribute("admin.attempts");
        request.changeSessionId(); session.setAttribute("admin.signedIn", Instant.now());
        session.setAttribute("admin.authVersion",authenticatedVersion);session.removeAttribute("admin.pendingVersion");
        session.removeAttribute("admin.recovered");if(input.code().length()==32)session.setAttribute("admin.recovered",Instant.now());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, List.of(new SimpleGrantedAuthority("ROLE_"+actor.role().name()))));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        csrf.saveToken(null, request, response);
        return actor;
    }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response, @AuthenticationPrincipal AdminIdentity actor) {
        if (actor != null) accounts.audit(actor.id(), "LOGOUT", actor.id());
        var session = request.getSession(false); if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        contexts.saveContext(SecurityContextHolder.createEmptyContext(), request, response);
    }
    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales o código inválidos, vencidos o acceso temporalmente bloqueado.");
    }
}
