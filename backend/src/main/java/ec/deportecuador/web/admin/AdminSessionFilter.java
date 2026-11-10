package ec.deportecuador.web.admin;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

/** Re-evaluate permissions on every request; disabled users lose access immediately. */
public class AdminSessionFilter extends OncePerRequestFilter {
    private final AdminAccounts accounts;
    public AdminSessionFilter(AdminAccounts accounts) { this.accounts = accounts; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AdminIdentity previous) {
            HttpSession session = request.getSession(false);
            Instant signedIn = session == null ? null : (Instant)session.getAttribute("admin.signedIn");
            var current = accounts.identity(previous.id());
            Long version=session==null?null:(Long)session.getAttribute("admin.authVersion");
            if (current.isEmpty() || signedIn == null || version==null || version!=accounts.authVersion(previous.id()) || signedIn.plusSeconds(28800).isBefore(Instant.now())) {
                SecurityContextHolder.clearContext();
                if (session != null) session.invalidate();
            } else {
                var identity = current.get();
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(identity, null, List.of(new SimpleGrantedAuthority("ROLE_" + identity.role().name()))));
            }
        }
        chain.doFilter(request, response);
    }
}
