package ec.deportecuador.web.admin;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
public class GoogleLogin {
 private final GoogleIdentity google;private final AdminAccounts accounts;private final SecurityContextRepository contexts;
 public GoogleLogin(GoogleIdentity google,AdminAccounts accounts,SecurityContextRepository contexts){this.google=google;this.accounts=accounts;this.contexts=contexts;}
 public void success(HttpServletRequest request,HttpServletResponse response,Authentication authentication)throws IOException{
  try {
   var session=request.getSession();var user=(OidcUser)authentication.getPrincipal();UUID id;
   if(session.getAttribute("admin.googleLink") instanceof UUID link){
    if(!(session.getAttribute("admin.googleLinkExpires") instanceof Instant expires)||!expires.isAfter(Instant.now()))throw AccountRecovery.invalid();
    id=google.link(link,(Long)session.getAttribute("admin.googleLinkVersion"),user);
   }else id=google.find(user);
   // Discard provider tokens and all previous authentication before the local second factor.
   session.invalidate();SecurityContextHolder.clearContext();contexts.saveContext(SecurityContextHolder.createEmptyContext(),request,response);
   session=request.getSession(true);session.setAttribute("admin.pending",id);session.setAttribute("admin.pendingVersion",accounts.authVersion(id));session.setAttribute("admin.expires",Instant.now().plusSeconds(300));
   response.sendRedirect("/admin/?mfa=1");
  }catch(RuntimeException error){failure(request,response);}
 }
 public void failure(HttpServletRequest request,HttpServletResponse response)throws IOException{var session=request.getSession(false);if(session!=null)session.invalidate();SecurityContextHolder.clearContext();contexts.saveContext(SecurityContextHolder.createEmptyContext(),request,response);response.sendRedirect("/admin/?authError=1");}
 @Configuration
 @ConditionalOnProperty(name="app.auth.google.enabled",havingValue="true")
 static class Registration {
  @Bean ClientRegistrationRepository googleRegistration(@Value("${app.auth.google.client-id}") String id,@Value("${app.auth.google.client-secret}") String secret,@Value("${app.auth.google.redirect-uri}") String redirect){
   if(id.isBlank()||secret.isBlank()||!redirect.matches("https://[a-zA-Z0-9.-]+(?::[0-9]+)?/login/oauth2/code/google|http://localhost:[0-9]+/login/oauth2/code/google"))throw new IllegalStateException("Configure Google OIDC credentials and redirect URI");
   return new InMemoryClientRegistrationRepository(CommonOAuth2Provider.GOOGLE.getBuilder("google").clientId(id).clientSecret(secret).scope("openid","profile","email").redirectUri(redirect).build());
  }
 }
}
