package ec.deportecuador.web;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:intake;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","app.storage.root=/tmp/deportecuador-test-files","app.mail.enabled=false"})
@AutoConfigureMockMvc class IntakeTest {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc;
 String body="""
 {"type":"DEMO","organization":"Test sports organization","country":"Ecuador","name":"Example Contact","email":"test@example.org","phone":"","description":"","interests":["asistodep"],"locale":"es","consent":true,"policyVersion":"draft-2026-10-19","website":""}
 """;
 @Test void savesRequestAndOutboxAtomicallyAndRetryDoesNotDuplicate()throws Exception{
  String key=UUID.randomUUID().toString();int before=jdbc.queryForObject("SELECT count(*) FROM commercial_request",Integer.class);
  for(int i=0;i<2;i++)mvc.perform(post("/api/v1/requests").header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNotEmpty());
  assertEquals(before+1,jdbc.queryForObject("SELECT count(*) FROM commercial_request",Integer.class));
  assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM notification_outbox WHERE request_id=(SELECT id FROM commercial_request WHERE request_key=?)",Integer.class,UUID.fromString(key)));
 }
 @Test void rejectsMissingConsentAndWrongProductType()throws Exception{
  for(String invalid:new String[]{body.replace("\"consent\":true","\"consent\":false"),body.replace("asistodep","consultoria"),body.replace("\"website\":\"\"","\"website\":\"bot\"")})mvc.perform(post("/api/v1/requests").header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
 }
 @Test void privateReadAccessIsDenied()throws Exception{mvc.perform(get("/api/v1/requests")).andExpect(status().isForbidden());}
}
