package ec.deportecuador.web.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:oidc;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","app.storage.root=target/oidc-test-files","app.mail.enabled=false","app.auth.google.enabled=true","app.auth.google.client-id=test-client","app.auth.google.client-secret=test-secret","app.auth.google.redirect-uri=http://localhost:8081/login/oauth2/code/google"})
@AutoConfigureMockMvc
class GoogleLoginTest {
 @Autowired MockMvc mvc;
 @Test void authorizationUsesGoogleStateNonceAndConfiguredRedirect() throws Exception {
  mvc.perform(get("/oauth2/authorization/google")).andExpect(status().is3xxRedirection()).andExpect(header().string("Location",allOf(startsWith("https://accounts.google.com/o/oauth2/v2/auth?"),containsString("state="),containsString("nonce="),containsString("scope=openid"),containsString("redirect_uri=http://localhost:8081/login/oauth2/code/google"))));
 }
 @Test void callbackWithoutSavedStateCannotAuthenticate() throws Exception {
  mvc.perform(get("/login/oauth2/code/google").param("state","invalid").param("code","invalid")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/?authError=1"));
  mvc.perform(get("/api/admin/auth/me")).andExpect(status().isUnauthorized());
 }
}
