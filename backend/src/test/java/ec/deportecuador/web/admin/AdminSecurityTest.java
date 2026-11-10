package ec.deportecuador.web.admin;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:admin;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "spring.datasource.username=sa", "spring.datasource.password=", "app.storage.root=target/admin-test-files", "app.mail.enabled=false"})
@AutoConfigureMockMvc
class AdminSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired AdminSecrets secrets;
    @Autowired AdminAccounts accounts;
    @Autowired CrmService crm;
    @Autowired EditorialService editorial;
    @Autowired MediaService media;
    @Autowired AccountRecovery recovery;
    @Autowired GoogleIdentity google;
    @Autowired GoogleLogin googleLogin;
    private static final String SECRET="GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    private static final String PASSWORD="Local-test-password-42";
    UUID admin, commercial, other, editor, viewer, request;

    String resetToken(UUID id) throws Exception {
        recovery.requestPassword(id+"@example.org");
        String body=secrets.decrypt(db.queryForObject("SELECT m.payload FROM admin_auth_mail m JOIN admin_password_reset r ON r.token_hash=m.token_hash WHERE r.user_id=? ORDER BY r.created_at DESC LIMIT 1",String.class,id));
        return java.net.URLDecoder.decode(body.split("#reset=")[1].split("\\n")[0],java.nio.charset.StandardCharsets.UTF_8);
    }
    @Test void passwordResetIsSingleUseKeepsMfaAndRevokesSessions() throws Exception {
        var old=session(admin);var pending=new MockHttpSession();
        mutation(post("/api/admin/auth/login"),pending,Map.of("email",admin+"@example.org","password",PASSWORD)).andExpect(status().isOk());
        String token=resetToken(admin);String secretBefore=db.queryForObject("SELECT totp_secret FROM admin_user WHERE id=?",String.class,admin);
        mutation(post("/api/admin/auth/password/reset"),new MockHttpSession(),Map.of("token",token,"password","New-private-password-123")).andExpect(status().isOk());
        assertEquals(secretBefore,db.queryForObject("SELECT totp_secret FROM admin_user WHERE id=?",String.class,admin));
        assertNull(accounts.checkPassword(admin+"@example.org",PASSWORD));assertEquals(admin,accounts.checkPassword(admin+"@example.org","New-private-password-123"));
        mvc.perform(get("/api/admin/auth/me").session(old)).andExpect(status().isUnauthorized());
        mutation(post("/api/admin/auth/mfa"),pending,Map.of("code",Totp.code(SECRET,Instant.now().getEpochSecond()/30))).andExpect(status().isUnauthorized());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->recovery.resetPassword(token,PASSWORD));
    }
    @Test void resetRequestIsGenericThrottledAndRequiresCsrf() throws Exception {
        mvc.perform(post("/api/admin/auth/password/request").contentType("application/json").content("{\"email\":\"unknown@example.org\"}")).andExpect(status().isForbidden());
        var known=mutation(post("/api/admin/auth/password/request"),new MockHttpSession(),Map.of("email",admin+"@example.org")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var unknown=mutation(post("/api/admin/auth/password/request"),new MockHttpSession(),Map.of("email","unknown@example.org")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertEquals(known,unknown);
        recovery.requestPassword(admin+"@example.org");assertEquals(1,db.queryForObject("SELECT count(*) FROM admin_password_reset",Integer.class));
        String token=resetToken(editor);db.update("UPDATE admin_password_reset SET expires_at=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->recovery.resetPassword(token,PASSWORD));
    }
    @Test void recoveryMailIsDeliveredAndEncryptedPayloadIsRemoved() throws Exception {
        resetToken(admin);
        var sender=org.mockito.Mockito.mock(org.springframework.mail.javamail.JavaMailSender.class);
        var message=new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties()));
        org.mockito.Mockito.when(sender.createMimeMessage()).thenReturn(message);
        var delivery=new AuthMail(db,secrets,sender);delivery.enabled=true;delivery.from="test@example.org";delivery.deliver();
        org.mockito.Mockito.verify(sender).send(message);assertTrue(message.getContent().toString().contains("/admin/#reset="));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM admin_auth_mail WHERE sent_at IS NULL OR payload<>''",Integer.class));
        delivery.deliver();org.mockito.Mockito.verify(sender,org.mockito.Mockito.times(1)).send(message);
    }
    @Test void recoveryCodesAreHashedSingleUseAndRequirePasswordThenFactor() throws Exception {
        var codes=recovery.codes(accounts.identity(admin).orElseThrow());assertEquals(10,codes.size());
        assertFalse(db.queryForList("SELECT code_hash FROM admin_recovery_code",String.class).contains(codes.get(0)));
        var pending=new MockHttpSession();mutation(post("/api/admin/auth/login"),pending,Map.of("email",admin+"@example.org","password",PASSWORD)).andExpect(status().isOk());
        mutation(post("/api/admin/auth/mfa"),pending,Map.of("code",codes.get(0))).andExpect(status().isOk());assertEquals(9,recovery.remaining(admin));assertNull(accounts.checkFactor(admin,codes.get(0)));
        recovery.codes(accounts.identity(admin).orElseThrow());assertNull(accounts.checkFactor(admin,codes.get(1)));
        mutation(post("/api/admin/auth/security/codes"),session(admin),Map.of("password","incorrect-password","code",codes.get(2))).andExpect(status().isBadRequest());assertEquals(10,recovery.remaining(admin));
    }
    @Test void replacingMfaRequiresConfirmationAndRevokesOldCredentials() throws Exception {
        var actor=accounts.identity(admin).orElseThrow();var old=session(admin);var codes=recovery.codes(actor);String next=Totp.newSecret();
        assertFalse(recovery.replaceMfa(actor,0,next,"invalid"));assertEquals(10,recovery.remaining(admin));
        assertTrue(recovery.replaceMfa(actor,0,next,Totp.code(next,Instant.now().getEpochSecond()/30)));assertEquals(0,recovery.remaining(admin));assertNull(accounts.checkFactor(admin,codes.get(0)));
        mvc.perform(get("/api/admin/auth/me").session(old)).andExpect(status().isUnauthorized());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->recovery.replaceMfa(actor,0,next,"000000"));
    }
    org.springframework.security.oauth2.core.oidc.user.OidcUser googleUser(String sub,String domain,boolean verified){
        var claims=new HashMap<String,Object>();claims.put("iss","https://accounts.google.com");claims.put("sub",sub);claims.put("email",admin+"@example.org");claims.put("email_verified",verified);claims.put("hd",domain);
        var token=new org.springframework.security.oauth2.core.oidc.OidcIdToken("test-token",Instant.now(),Instant.now().plusSeconds(300),claims);
        return new org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")),token);
    }
    @Test void googleNeverLinksByEmailAndEnforcesWorkspaceAndUniqueSubject(){
        org.springframework.test.util.ReflectionTestUtils.setField(google,"enabled",true);
        try {
            var user=googleUser("subject-one","deportecuador.com.ec",true);
            assertThrows(org.springframework.web.server.ResponseStatusException.class,()->google.find(user));
            assertThrows(org.springframework.web.server.ResponseStatusException.class,()->google.link(admin,1,user));
            google.link(admin,0,user);assertEquals(admin,google.find(user));
            assertThrows(org.springframework.web.server.ResponseStatusException.class,()->google.find(googleUser("subject-two","deportecuador.com.ec",true)));
            assertThrows(org.springframework.web.server.ResponseStatusException.class,()->google.find(googleUser("subject-one","other.example",true)));
            assertThrows(org.springframework.web.server.ResponseStatusException.class,()->google.find(googleUser("subject-one","deportecuador.com.ec",false)));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->google.link(editor,0,user));
            google.unlink(accounts.identity(admin).orElseThrow());assertFalse(google.linked(admin));assertEquals(1,accounts.authVersion(admin));
        }finally{org.springframework.test.util.ReflectionTestUtils.setField(google,"enabled",false);}
    }
    @Test void accountSecurityRequiresSessionAndCsrf() throws Exception {
        mvc.perform(get("/api/admin/auth/security")).andExpect(status().isUnauthorized());
        for(UUID id:List.of(admin,editor,viewer,commercial))mvc.perform(get("/api/admin/auth/security").session(session(id))).andExpect(status().isOk());
        mvc.perform(post("/api/admin/auth/security/codes").session(session(admin)).contentType("application/json").content("{}")).andExpect(status().isForbidden());
    }
    @Test void googleCallbackDiscardsProviderSessionAndRequiresLocalMfa() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(google,"enabled",true);
        try {
            var user=googleUser("linked-subject","deportecuador.com.ec",true);google.link(admin,0,user);
            var request=new org.springframework.mock.web.MockHttpServletRequest();var response=new org.springframework.mock.web.MockHttpServletResponse();var prior=new MockHttpSession();request.setSession(prior);prior.setAttribute("provider-private-token","not-retained");
            googleLogin.success(request,response,new UsernamePasswordAuthenticationToken(user,null,user.getAuthorities()));
            assertTrue(prior.isInvalid());assertNull(request.getSession().getAttribute("provider-private-token"));assertNull(SecurityContextHolder.getContext().getAuthentication());assertEquals(admin,request.getSession().getAttribute("admin.pending"));
            mvc.perform(get("/api/admin/auth/me").session((MockHttpSession)request.getSession())).andExpect(status().isUnauthorized());
            mutation(post("/api/admin/auth/mfa"),(MockHttpSession)request.getSession(),Map.of("code",Totp.code(SECRET,Instant.now().getEpochSecond()/30))).andExpect(status().isOk());
        } finally {org.springframework.test.util.ReflectionTestUtils.setField(google,"enabled",false);SecurityContextHolder.clearContext();}
    }

    @Test void assistantConfigurationAndAgendaAreAdminOnly() throws Exception {
        mvc.perform(get("/api/admin/assistant-settings").session(session(admin))).andExpect(status().isOk());
        for(UUID id:List.of(commercial,editor,viewer)){
            mvc.perform(get("/api/admin/assistant-settings").session(session(id))).andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/appointments").session(session(id))).andExpect(status().isForbidden());
        }
        mvc.perform(put("/api/admin/assistant-settings").session(session(admin)).contentType("application/json").content("{}")).andExpect(status().isForbidden());
    }

    @BeforeEach void seed() {
        db.update("DELETE FROM cms_revision_media");db.update("DELETE FROM media_asset");
        db.update("DELETE FROM cms_event");db.update("DELETE FROM cms_revision WHERE revision>1");
        db.update("UPDATE cms_document SET state='PUBLISHED',visible=TRUE,version=1,working_revision=1,published_revision=1");
        db.update("DELETE FROM crm_notice"); db.update("DELETE FROM crm_activity");
        db.update("DELETE FROM admin_audit"); db.update("DELETE FROM admin_invitation");
        db.update("DELETE FROM notification_outbox"); db.update("DELETE FROM commercial_request"); db.update("DELETE FROM admin_auth_mail");db.update("DELETE FROM admin_password_reset");db.update("DELETE FROM admin_recovery_code");db.update("DELETE FROM admin_oidc_identity");db.update("DELETE FROM admin_user");
        db.update("UPDATE admin_setup SET completed=TRUE WHERE id=1");
        admin=user("ADMIN"); commercial=user("COMMERCIAL"); other=user("COMMERCIAL"); editor=user("EDITOR"); viewer=user("VIEWER");
        request=addRequest(commercial,"Assigned organization"); addRequest(other,"Private organization");
    }
    UUID user(String role) {
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO admin_user(id,email,display_name,role,password_hash,totp_secret,created_at) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP)", id,id+"@example.org",role,role,new BCryptPasswordEncoder(4).encode(PASSWORD),secrets.encrypt(SECRET));
        return id;
    }
    byte[] png() throws Exception {
        var image=new java.awt.image.BufferedImage(4,3,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);return bytes.toByteArray();
    }
    MediaService.Asset asset()throws Exception{return media.upload(accounts.identity(editor).orElseThrow(),new org.springframework.mock.web.MockMultipartFile("file","test.png","image/png",png()),"Imagen de prueba");}
    @Test void mediaPublicationFollowsRevisionVisibilityAndRestoration() throws Exception {
        var actor=accounts.identity(editor).orElseThrow();var asset=asset();
        mvc.perform(get("/api/v1/media/"+asset.id())).andExpect(status().isNotFound());
        var doc=editorial.detail(actor,"asistodep","es",0);var values=new LinkedHashMap<>(doc.body());values.put("mediaImage",asset.id().toString());values.put("mediaAlt","Imagen de prueba");
        editorial.save(actor,"asistodep","es",doc.document().version(),values);
        mvc.perform(get("/api/v1/media/"+asset.id())).andExpect(status().isNotFound());
        doc=editorial.detail(actor,"asistodep","es",0);editorial.action(actor,"asistodep","es",doc.document().version(),"PUBLISH",null);
        mvc.perform(get("/api/v1/media/"+asset.id())).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(header().string("Cache-Control","no-store"));
        media.update(actor,asset.id(),asset.version(),asset.title(),true);
        assertTrue(media.isPublic(asset.id()),"Archiving must preserve published references");
        doc=editorial.detail(actor,"asistodep","es",0);editorial.action(actor,"asistodep","es",doc.document().version(),"RESTORE",1);
        assertTrue(media.isPublic(asset.id()),"Restore creates only a draft");
        doc=editorial.detail(actor,"asistodep","es",0);editorial.action(actor,"asistodep","es",doc.document().version(),"PUBLISH",null);
        mvc.perform(get("/api/v1/media/"+asset.id())).andExpect(status().isNotFound());
        doc=editorial.detail(actor,"asistodep","es",0);editorial.action(actor,"asistodep","es",doc.document().version(),"RESTORE",2);
        doc=editorial.detail(actor,"asistodep","es",0);editorial.action(actor,"asistodep","es",doc.document().version(),"PUBLISH",null);
        assertTrue(media.isPublic(asset.id()));
        doc=editorial.detail(actor,"asistodep","es",0);editorial.action(actor,"asistodep","es",doc.document().version(),"HIDE",null);assertFalse(media.isPublic(asset.id()));
        assertEquals(4,db.queryForObject("SELECT count(*) FROM cms_revision WHERE content_key='asistodep' AND locale='es'",Integer.class));
    }
    @Test void mediaUploadAndPreviewRequireEditorialRoleAndCsrf() throws Exception {
        var file=new org.springframework.mock.web.MockMultipartFile("file","test.png","image/png",png());
        mvc.perform(multipart("/api/admin/media").file(file).param("title","Test").session(session(editor))).andExpect(status().isForbidden());
        var session=session(editor);var response=mvc.perform(get("/api/admin/auth/csrf").session(session)).andReturn();var csrf=json.readTree(response.getResponse().getContentAsString());
        mvc.perform(multipart("/api/admin/media").file(file).param("title","Test").session(session).header(csrf.get("headerName").asText(),csrf.get("token").asText())).andExpect(status().isCreated()).andExpect(jsonPath("$.width").value(4));
        var asset=asset();for(UUID id:List.of(commercial,viewer)){mvc.perform(get("/api/admin/media").session(session(id))).andExpect(status().isForbidden());mvc.perform(get("/api/admin/media/"+asset.id()+"/file").session(session(id))).andExpect(status().isForbidden());}
        mvc.perform(get("/api/admin/media/"+asset.id()+"/file").session(session(editor))).andExpect(status().isOk());
        mvc.perform(get("/api/admin/media")).andExpect(status().isUnauthorized());
    }
    @Test void invalidMediaIsRejectedAndImagesAreReencoded() throws Exception {
        var actor=accounts.identity(editor).orElseThrow();
        for(String name:List.of("script.svg","script.html","fake.png","fake.pdf"))assertThrows(org.springframework.web.server.ResponseStatusException.class,()->media.upload(actor,new org.springframework.mock.web.MockMultipartFile("file",name,"image/png","<script>bad()</script>".getBytes()),"Test"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->media.upload(actor,new org.springframework.mock.web.MockMultipartFile("file","big.pdf","application/pdf",new byte[MediaService.MAX_BYTES+1]),"Test"));
        byte[] source=png();byte[] trailer="<script>not retained</script>".getBytes();byte[] combined=java.util.Arrays.copyOf(source,source.length+trailer.length);System.arraycopy(trailer,0,combined,source.length,trailer.length);
        var asset=media.upload(actor,new org.springframework.mock.web.MockMultipartFile("file","../../test.png","text/html",combined),"Test");assertEquals("test.png",asset.filename());assertFalse(new String(media.read(asset.id()),java.nio.charset.StandardCharsets.ISO_8859_1).contains("<script>"));assertEquals(1,db.queryForObject("SELECT count(*) FROM media_asset",Integer.class));
    }
    @Test void pdfDownloadsAsAttachmentAndMetadataUsesVersions() throws Exception {
        var actor=accounts.identity(editor).orElseThrow();var asset=media.upload(actor,new org.springframework.mock.web.MockMultipartFile("file","guide.pdf","application/pdf","%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes()),"Guide");
        mvc.perform(get("/api/admin/media/"+asset.id()+"/file").session(session(editor))).andExpect(status().isOk()).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("attachment;"))).andExpect(header().string("X-Content-Type-Options","nosniff"));
        media.update(actor,asset.id(),1,"Updated",true);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->media.update(actor,asset.id(),1,"Stale",false));assertEquals(1,media.list(actor,"Updated",true,0).total());
        var doc=editorial.detail(actor,"home","es",0);var body=new LinkedHashMap<>(doc.body());body.put("mediaImage",asset.id().toString());body.put("mediaAlt","Wrong type");assertThrows(org.springframework.web.server.ResponseStatusException.class,()->editorial.save(actor,"home","es",doc.document().version(),body));
    }
    UUID addRequest(UUID assignee,String organization) {
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO commercial_request(id,request_key,request_type,organization,country,contact_name,email,phone,description,interests,locale,policy_version,consent_at,created_at,assigned_to) VALUES(?,?,'DEMO',?,'Ecuador','Test','private@example.org','','','asistodep','es','draft-2026-10-19',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?)",id,UUID.randomUUID(),organization,assignee);
        return id;
    }
    MockHttpSession session(UUID id) {
        var identity=accounts.identity(id).orElseThrow();
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(identity,null,List.of(new SimpleGrantedAuthority("ROLE_"+identity.role()))));
        var session=new MockHttpSession(); session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);session.setAttribute("admin.signedIn",Instant.now());session.setAttribute("admin.authVersion",0L);return session;
    }
    ResultActions mutation(MockHttpServletRequestBuilder builder,MockHttpSession session,Object payload) throws Exception {
        var result=mvc.perform(get("/api/admin/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        var token=json.readTree(result.getResponse().getContentAsString());
        return mvc.perform(builder.session(session).header(token.get("headerName").asText(),token.get("token").asText()).contentType("application/json").content(json.writeValueAsString(payload)));
    }
    @Test void anonymousAndPasswordOnlyCannotReadContacts() throws Exception {
        mvc.perform(get("/api/admin/requests")).andExpect(status().isUnauthorized());
        var session=new MockHttpSession();
        mutation(post("/api/admin/auth/login"),session,Map.of("email",admin+"@example.org","password",PASSWORD)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/requests").session(session)).andExpect(status().isUnauthorized());
        mutation(post("/api/admin/auth/mfa"),session,Map.of("code",Totp.code(SECRET,Instant.now().getEpochSecond()/30))).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(get("/api/admin/requests").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2));
        assertNull(accounts.checkMfa(admin,Totp.code(SECRET,Instant.now().getEpochSecond()/30)),"TOTP cannot be replayed");
        mutation(post("/api/admin/auth/logout"),session,Map.of()).andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
    }
    @Test void roleMatrixAndAssignedScopeAreEnforced() throws Exception {
        for(UUID id:List.of(editor,viewer)) {
            mvc.perform(get("/api/admin/requests").session(session(id))).andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/users").session(session(id))).andExpect(status().isForbidden());
        }
        var result=mvc.perform(get("/api/admin/requests").session(session(commercial))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("Private organization"));
        mutation(patch("/api/admin/requests/"+request+"/assignment"),session(commercial),Map.of("userId",other)).andExpect(status().isForbidden());
        mutation(patch("/api/admin/requests/"+request+"/assignment"),session(admin),Map.of("userId",other)).andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/requests").session(session(commercial))).andExpect(jsonPath("$.total").value(0));
        var report=mvc.perform(get("/api/admin/reports").session(session(viewer))).andExpect(status().isOk()).andReturn();
        assertFalse(report.getResponse().getContentAsString().contains("example.org"));
        mvc.perform(get("/api/admin/reports").session(session(editor))).andExpect(status().isForbidden());
    }
    @Test void csrfDisableAndLastAdministratorAreProtected() throws Exception {
        mvc.perform(post("/api/admin/users").session(session(admin)).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mutation(patch("/api/admin/users/"+admin),session(admin),Map.of("role","ADMIN","enabled",false)).andExpect(status().isConflict());
        var existing=session(commercial);
        mutation(patch("/api/admin/users/"+commercial),session(admin),Map.of("role","COMMERCIAL","enabled",false)).andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/auth/me").session(existing)).andExpect(status().isUnauthorized());
    }
    @Test void invitationsAreControlledSingleUseAndRequireMfa() throws Exception {
        mutation(post("/api/admin/users"),session(editor),Map.of("email","new@example.org","name","New","role","ADMIN")).andExpect(status().isForbidden());
        var result=mutation(post("/api/admin/users"),session(admin),Map.of("email","new@example.org","name","New","role","EDITOR")).andExpect(status().isCreated()).andReturn();
        String token=json.readTree(result.getResponse().getContentAsString()).get("token").asText();
        var session=new MockHttpSession();
        var payload=Map.of("token",token,"email","new@example.org","name","New","password",PASSWORD,"bootstrap",false);
        var enrolled=mutation(post("/api/admin/auth/enroll"),session,payload).andExpect(status().isOk()).andReturn();
        String secret=json.readTree(enrolled.getResponse().getContentAsString()).get("secret").asText();
        mvc.perform(get("/api/admin/auth/me").session(session)).andExpect(status().isUnauthorized());
        mutation(post("/api/admin/auth/mfa"),session,Map.of("code",Totp.code(secret,Instant.now().getEpochSecond()/30))).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("EDITOR"));
        mutation(post("/api/admin/auth/enroll"),new MockHttpSession(),payload).andExpect(status().isForbidden());
        assertFalse(db.queryForObject("SELECT totp_secret FROM admin_user WHERE email='new@example.org'",String.class).contains(secret));
    }
    @Test void firstAdminRequiresLocalTokenAndSetupCannotReopen() throws Exception {
        db.update("UPDATE admin_setup SET completed=FALSE WHERE id=1");
        var payload=new HashMap<String,Object>(Map.of("token","wrong","email","owner@example.org","name","Owner","password",PASSWORD,"bootstrap",true));
        mutation(post("/api/admin/auth/enroll"),new MockHttpSession(),payload).andExpect(status().isForbidden());
        payload.put("token",Files.readString(Path.of("target/admin-test-files/.admin-secrets/setup.token")));
        var session=new MockHttpSession();
        var response=mutation(post("/api/admin/auth/enroll"),session,payload).andExpect(status().isOk()).andReturn();
        String secret=json.readTree(response.getResponse().getContentAsString()).get("secret").asText();
        mutation(post("/api/admin/auth/mfa"),session,Map.of("code",Totp.code(secret,Instant.now().getEpochSecond()/30))).andExpect(status().isOk());
        mutation(post("/api/admin/auth/enroll"),new MockHttpSession(),payload).andExpect(status().isForbidden());
    }
    @Test void failedPasswordsLockAccountAndExpiredSessionsAreDenied() throws Exception {
        for(int i=0;i<5;i++) assertNull(accounts.checkPassword(admin+"@example.org","wrong-password"));
        assertNull(accounts.checkPassword(admin+"@example.org",PASSWORD));
        var session=session(viewer);session.setAttribute("admin.signedIn",Instant.now().minusSeconds(28801));
        mvc.perform(get("/api/admin/auth/me").session(session)).andExpect(status().isUnauthorized());
    }
    @Test void totpMatchesRfcVectors() {
        long[] timestamps={59,1111111109L,1111111111L,1234567890L,2000000000L,20000000000L};
        String[] codes={"287082","081804","050471","005924","279037","353130"};
        for(int i=0;i<timestamps.length;i++) assertEquals(codes[i],Totp.code(SECRET,timestamps[i]/30));
    }
    @Test void crmScopeTransitionsAndConcurrentEdits() throws Exception {
        String path="/api/admin/requests/"+request;
        mvc.perform(get(path+"/crm").session(session(other))).andExpect(status().isNotFound());
        for(UUID id:List.of(editor,viewer)) {
            mvc.perform(get(path+"/crm").session(session(id))).andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/notifications").session(session(id))).andExpect(status().isForbidden());
        }
        mutation(post(path+"/activities"),session(other),Map.of("kind","NOTE","body","Forbidden")).andExpect(status().isNotFound());
        mutation(post(path+"/activities"),session(commercial),Map.of("kind","CALL","body","Called test contact")).andExpect(status().isCreated());
        mutation(patch(path+"/workflow"),session(commercial),Map.of("version",0,"status","WON","closeReason","Invalid jump")).andExpect(status().isBadRequest());
        mutation(patch(path+"/workflow"),session(commercial),Map.of("version",0,"status","CONTACTED","nextContactAt",Instant.now().plusSeconds(3600).toString())).andExpect(status().isNoContent());
        mutation(patch(path+"/workflow"),session(commercial),Map.of("version",0,"status","PROPOSAL")).andExpect(status().isConflict());
        mutation(patch(path+"/workflow"),session(commercial),Map.of("version",1,"status","WON")).andExpect(status().isBadRequest());
        mutation(patch(path+"/workflow"),session(commercial),Map.of("version",1,"status","WON","closeReason","Accepted proposal")).andExpect(status().isNoContent());
        assertNull(db.queryForObject("SELECT next_contact_at FROM commercial_request WHERE id=?",java.sql.Timestamp.class,request));
        mutation(patch(path+"/workflow"),session(commercial),Map.of("version",2,"status","CONTACTED")).andExpect(status().isBadRequest());
        mutation(patch(path+"/workflow"),session(admin),Map.of("version",2,"status","CONTACTED")).andExpect(status().isNoContent());
        mvc.perform(get(path+"/crm").session(session(commercial))).andExpect(jsonPath("$.activities.length()").value(4));
        assertNull(db.queryForObject("SELECT close_reason FROM commercial_request WHERE id=?",String.class,request));
    }
    @Test void crmRemindersAreDurableUniqueAndReassignmentRevokesAccess() throws Exception {
        var owner=accounts.identity(admin).orElseThrow();
        crm.assign(owner,request,other);crm.assign(owner,request,other);
        assertEquals(1,db.queryForObject("SELECT count(*) FROM crm_notice",Integer.class));
        db.update("UPDATE commercial_request SET next_contact_at=? WHERE id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(120)),request);
        crm.scheduleDue();crm.scheduleDue();
        assertEquals(2,db.queryForObject("SELECT count(*) FROM crm_notice",Integer.class));
        mvc.perform(get("/api/admin/requests?due=true").session(session(other))).andExpect(jsonPath("$.total").value(1));
        var former=accounts.identity(other).orElseThrow();assertEquals(2,crm.notices(former).size());
        crm.assign(owner,request,commercial);
        assertEquals(0,crm.notices(former).size());
        mutation(post("/api/admin/requests/"+request+"/activities"),session(other),Map.of("kind","NOTE","body","Cannot edit anymore")).andExpect(status().isNotFound());
        crm.scheduleDue();crm.scheduleDue();
        assertEquals(2,crm.notices(accounts.identity(commercial).orElseThrow()).size());
        crm.update(owner,request,2,"LOST",null,"No longer needed");
        assertTrue(crm.notices(accounts.identity(commercial).orElseThrow()).stream().noneMatch(n->n.kind().equals("FOLLOWUP")));
    }
    @Test void crmMailRetriesAndDoesNotSendObsoleteAssignments() throws Exception {
        var mail=org.mockito.Mockito.mock(org.springframework.mail.javamail.JavaMailSender.class);
        org.mockito.Mockito.when(mail.createMimeMessage()).thenAnswer(invocation->new jakarta.mail.internet.MimeMessage((jakarta.mail.Session)null));
        var worker=new CrmNotifications(crm,db,mail,true,"test@example.org");
        var owner=accounts.identity(admin).orElseThrow();
        crm.assign(owner,request,other);
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("Simulated unavailable SMTP")).when(mail).send(org.mockito.ArgumentMatchers.any(jakarta.mail.internet.MimeMessage.class));
        worker.deliver();
        assertEquals(1,db.queryForObject("SELECT attempts FROM crm_notice WHERE cancelled=FALSE",Integer.class));
        assertNull(db.queryForObject("SELECT sent_at FROM crm_notice WHERE cancelled=FALSE",java.sql.Timestamp.class));
        crm.assign(owner,request,commercial);
        org.mockito.Mockito.reset(mail);
        org.mockito.Mockito.when(mail.createMimeMessage()).thenAnswer(invocation->new jakarta.mail.internet.MimeMessage((jakarta.mail.Session)null));
        worker.deliver();worker.deliver();
        var capture=org.mockito.ArgumentCaptor.forClass(jakarta.mail.internet.MimeMessage.class);
        org.mockito.Mockito.verify(mail,org.mockito.Mockito.times(1)).send(capture.capture());
        assertEquals(commercial+"@example.org",capture.getValue().getAllRecipients()[0].toString());
        assertFalse(capture.getValue().getContent().toString().contains("private@example.org"));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM crm_notice WHERE sent_at IS NOT NULL",Integer.class));
    }
    @Test void editorialDraftPublicationLocaleAndRestorationAreIndependent() throws Exception {
        var actor=accounts.identity(editor).orElseThrow();
        var initial=editorial.detail(actor,"asistodep","es",0);
        var originalEn=editorial.published("en").get("asistodep");
        var draft=new HashMap<>(initial.body());draft.put("description","Draft only <script>alert(1)</script>");
        mutation(put("/api/admin/content/es/asistodep"),session(editor),Map.of("version",1,"body",draft)).andExpect(status().isNoContent());
        assertEquals(initial.body(),editorial.published("es").get("asistodep"));
        mutation(post("/api/admin/content/es/asistodep/actions"),session(editor),Map.of("version",2,"action","PUBLISH")).andExpect(status().isNoContent());
        assertEquals(draft,editorial.published("es").get("asistodep"));assertEquals(originalEn,editorial.published("en").get("asistodep"));
        mutation(post("/api/admin/content/es/asistodep/actions"),session(admin),Map.of("version",3,"action","RESTORE","revision",1)).andExpect(status().isNoContent());
        assertEquals(draft,editorial.published("es").get("asistodep"),"Restoring does not publish");
        var restored=editorial.detail(actor,"asistodep","es",0);assertEquals(3,restored.document().workingRevision());assertEquals(initial.body(),restored.body());assertEquals(3,restored.events().size());
        assertEquals(draft,editorial.revision(actor,"asistodep","es",2));
        editorial.action(actor,"asistodep","es",4,"PUBLISH",null);
        assertEquals(initial.body(),editorial.published("es").get("asistodep"));
    }
    @Test void editorialVisibilityArchiveAndStaleVersionsAreProtected() throws Exception {
        var actor=accounts.identity(admin).orElseThrow();
        editorial.action(actor,"about","pt",1,"HIDE",null);
        assertFalse(editorial.published("pt").containsKey("about"));
        assertTrue(editorial.published("es").containsKey("about"));
        mutation(post("/api/admin/content/pt/about/actions"),session(editor),Map.of("version",1,"action","SHOW")).andExpect(status().isConflict());
        editorial.action(actor,"about","pt",2,"ARCHIVE",null);
        editorial.action(actor,"about","pt",3,"SHOW",null);
        assertFalse(editorial.published("pt").containsKey("about"),"Visibility does not publish an archive");
        editorial.action(actor,"about","pt",4,"PUBLISH",null);
        assertTrue(editorial.published("pt").containsKey("about"));
        editorial.action(actor,"about","pt",5,"UNPUBLISH",null);
        assertFalse(editorial.published("pt").containsKey("about"));
    }
    @Test void editorialRoleMatrixValidationAndPublicContract() throws Exception {
        mvc.perform(get("/api/admin/content?locale=es")).andExpect(status().isUnauthorized());
        for(UUID id:List.of(commercial,viewer)){
            mvc.perform(get("/api/admin/content?locale=es").session(session(id))).andExpect(status().isForbidden());
            mutation(post("/api/admin/content/es/home/actions"),session(id),Map.of("version",1,"action","PUBLISH")).andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/content/es/home/revisions/1").session(session(id))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/admin/content?locale=es").session(session(editor))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(15));
        mutation(put("/api/admin/content/es/home"),session(editor),Map.of("version",1,"body",Map.of("unknown","text"))).andExpect(status().isBadRequest());
        var body=new HashMap<>(editorial.detail(accounts.identity(editor).orElseThrow(),"home","es",0).body());body.put("metaTitle","x".repeat(181));
        mutation(put("/api/admin/content/es/home"),session(editor),Map.of("version",1,"body",body)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/content/es")).andExpect(status().isOk()).andExpect(jsonPath("$.home.metaTitle").isNotEmpty()).andExpect(jsonPath("$.home.version").doesNotExist());
        mvc.perform(get("/api/v1/content/invalid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/requests").session(session(editor))).andExpect(status().isForbidden());
    }
}
