package ec.deportecuador.web;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpMethod;
import ec.deportecuador.web.admin.*;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
@Configuration class Security {
 @Bean SecurityContextRepository contexts() { return new HttpSessionSecurityContextRepository(); }
 @Bean CsrfTokenRepository csrfTokens() { return new HttpSessionCsrfTokenRepository(); }
 @Bean SecurityFilterChain chain(HttpSecurity http, AdminAccounts accounts, SecurityContextRepository contexts, CsrfTokenRepository csrf, org.springframework.beans.factory.ObjectProvider<org.springframework.security.oauth2.client.registration.ClientRegistrationRepository> registrations, GoogleLogin google) throws Exception {
  if(registrations.getIfAvailable()!=null)http.oauth2Login(o->o.clientRegistrationRepository(registrations.getObject()).authorizedClientRepository(new org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository()).successHandler(google::success).failureHandler((request,response,error)->google.failure(request,response)));
  return http.securityContext(c -> c.securityContextRepository(contexts))
   .csrf(c->c.csrfTokenRepository(csrf).ignoringRequestMatchers("/api/v1/requests","/api/v1/assistant/message","/api/v1/assistant/bookings","/api/whatsapp/webhook"))
   .requestCache(c -> c.disable())
   .authorizeHttpRequests(a->a
    .requestMatchers(HttpMethod.POST,"/api/v1/requests").permitAll()
    .requestMatchers(HttpMethod.POST,"/api/v1/assistant/message","/api/v1/assistant/bookings").permitAll()
    .requestMatchers(HttpMethod.GET,"/api/v1/assistant/config","/api/v1/assistant/slots").permitAll()
    .requestMatchers("/api/admin/appointments/**").hasRole("ADMIN")
    .requestMatchers("/api/admin/assistant-settings").hasRole("ADMIN")
    .requestMatchers(HttpMethod.GET,"/api/whatsapp/webhook").permitAll()
    .requestMatchers(HttpMethod.POST,"/api/whatsapp/webhook").permitAll()
    .requestMatchers(HttpMethod.GET,"/api/v1/content/*").permitAll()
    .requestMatchers(HttpMethod.GET,"/api/v1/media/*").permitAll()
    .requestMatchers("/api/admin/media/**").hasAnyRole("ADMIN","EDITOR")
    .requestMatchers("/actuator/health").permitAll()
    .requestMatchers("/oauth2/authorization/google","/login/oauth2/code/google").permitAll()
    .requestMatchers(HttpMethod.GET,"/api/admin/auth/csrf","/api/admin/auth/status").permitAll()
    .requestMatchers(HttpMethod.POST,"/api/admin/auth/login","/api/admin/auth/enroll","/api/admin/auth/mfa","/api/admin/auth/password/request","/api/admin/auth/password/reset").permitAll()
    .requestMatchers("/api/admin/users/**","/api/admin/audit").hasRole("ADMIN")
    .requestMatchers("/api/admin/content/**").hasAnyRole("ADMIN","EDITOR")
    .requestMatchers(HttpMethod.PATCH,"/api/admin/requests/*/assignment").hasRole("ADMIN")
    .requestMatchers("/api/admin/requests/**").hasAnyRole("ADMIN","COMMERCIAL")
    .requestMatchers("/api/admin/notifications/**").hasAnyRole("ADMIN","COMMERCIAL")
    .requestMatchers("/api/admin/reports").hasAnyRole("ADMIN","VIEWER")
    .requestMatchers("/api/admin/auth/security/**").hasAnyRole("ADMIN","EDITOR","COMMERCIAL","VIEWER")
    .requestMatchers("/api/admin/auth/me","/api/admin/auth/logout").authenticated()
    .anyRequest().denyAll())
   .exceptionHandling(e -> e.authenticationEntryPoint((request,response,error) -> response.setStatus(request.getRequestURI().startsWith("/api/admin/") ? 401 : 403)))
   .addFilterBefore(new AdminSessionFilter(accounts), AuthorizationFilter.class)
   .formLogin(f->f.disable()).httpBasic(b->b.disable()).logout(l->l.disable()).build();
 }
}
