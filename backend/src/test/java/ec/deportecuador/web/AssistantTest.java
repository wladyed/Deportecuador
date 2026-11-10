package ec.deportecuador.web;

import ec.deportecuador.web.admin.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:assistant;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","app.storage.root=target/assistant-test-files","app.mail.enabled=false"})
@AutoConfigureMockMvc
class AssistantTest {
 @Autowired AppointmentService agenda;@Autowired AssistantSettings settings;@Autowired JdbcTemplate db;@Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired WhatsAppConversation conversations;@Autowired AdminSecrets secrets;
 @MockBean GoogleCalendarGateway calendar;
 AdminIdentity admin;Instant start;
 @BeforeEach void setup(){
  db.update("DELETE FROM whatsapp_reply");db.update("DELETE FROM whatsapp_session");db.update("DELETE FROM appointment_slot");db.update("DELETE FROM admin_audit");db.update("DELETE FROM notification_outbox");db.update("DELETE FROM commercial_request");db.update("DELETE FROM admin_user");
  UUID id=UUID.randomUUID();db.update("INSERT INTO admin_user(id,email,display_name,role,created_at) VALUES(?,?,'Test','ADMIN',CURRENT_TIMESTAMP)",id,"test@example.org");admin=new AdminIdentity(id,"test@example.org","Test",AdminIdentity.Role.ADMIN);
  db.update("UPDATE assistant_settings SET opens_at='06:00',closes_at='12:50',duration_minutes=30,weekdays='1,2,3,4,5,6,7',calendar_id='',calendar_enabled=FALSE,whatsapp_link_enabled=FALSE");
  start=LocalDate.now(ZoneId.of("America/Guayaquil")).plusDays(2).atTime(6,0).atZone(ZoneId.of("America/Guayaquil")).toInstant();
  when(calendar.available(anyList())).thenAnswer(i->i.getArgument(0));
 }
 Intake contact(){return new Intake("DEMO","Fictional club","Ecuador","Test Person","test@example.org","","Cita",List.of("asistodep"),"es",true,"draft-2026-10-19","");}
 @Test void bookingIsIdempotentAndPrivate()throws Exception{
  UUID slot=agenda.create(admin,start,start.plusSeconds(1800)),key=UUID.randomUUID();var first=agenda.reserve(key,slot,contact());assertEquals(first,agenda.reserve(key,slot,contact()));verify(calendar,times(1)).book(eq(key),any());assertEquals(1,db.queryForObject("SELECT count(*) FROM commercial_request",Integer.class));assertEquals(3,db.queryForObject("SELECT count(*) FROM notification_outbox",Integer.class));
  mvc.perform(get("/api/v1/assistant/slots")).andExpect(status().isOk()).andExpect(content().json("[]"));mvc.perform(get("/api/admin/appointments")).andExpect(status().isUnauthorized());mvc.perform(get("/api/admin/assistant-settings")).andExpect(status().isUnauthorized());
 }
 @Test void googleFailureNeverConfirmsOrPersists(){UUID slot=agenda.create(admin,start,start.plusSeconds(1800));doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)).when(calendar).book(any(),any());assertThrows(ResponseStatusException.class,()->agenda.reserve(UUID.randomUUID(),slot,contact()));assertEquals(0,db.queryForObject("SELECT count(*) FROM commercial_request",Integer.class));assertEquals("OPEN",db.queryForObject("SELECT state FROM appointment_slot WHERE id=?",String.class,slot));}
 @Test void concurrentBookingsHaveOneWinner()throws Exception{
  UUID slot=agenda.create(admin,start,start.plusSeconds(1800));var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
  Callable<Boolean> attempt=()->{gate.await();try{agenda.reserve(UUID.randomUUID(),slot,contact());return true;}catch(ResponseStatusException e){assertEquals(409,e.getStatusCode().value());return false;}};
  try{var a=pool.submit(attempt);var b=pool.submit(attempt);gate.countDown();assertNotEquals(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));assertEquals(1,db.queryForObject("SELECT count(*) FROM commercial_request",Integer.class));}finally{pool.shutdownNow();}
 }
 @Test void openingHoursOverlapAndRoleAreEnforced(){
  agenda.create(admin,start,start.plusSeconds(1800));assertThrows(ResponseStatusException.class,()->agenda.create(admin,start.plusSeconds(60),start.plusSeconds(1860)));
  assertThrows(ResponseStatusException.class,()->agenda.create(admin,start.minusSeconds(1800),start));assertThrows(ResponseStatusException.class,()->agenda.create(admin,start.plusSeconds(6*3600+30*60),start.plusSeconds(7*3600)));
  assertThrows(ResponseStatusException.class,()->agenda.create(new AdminIdentity(admin.id(),admin.email(),admin.name(),AdminIdentity.Role.EDITOR),start.plusSeconds(1800),start.plusSeconds(3600)));
 }
 @Test void invalidConsentAndCatalogCannotCreateGoogleEvents()throws Exception{
  UUID slot=agenda.create(admin,start,start.plusSeconds(1800));var data=new HashMap<String,Object>();data.put("slotId",slot);var invalid=json.convertValue(contact(),Map.class);invalid.put("consent",false);data.put("contact",invalid);
  mvc.perform(post("/api/v1/assistant/bookings").header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(json.writeValueAsString(data))).andExpect(status().isBadRequest());
  invalid.put("consent",true);invalid.put("interests",List.of("invented"));mvc.perform(post("/api/v1/assistant/bookings").header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(json.writeValueAsString(data))).andExpect(status().isBadRequest());verify(calendar,never()).book(any(),any());
 }
 @Test void cancelKeepsHistoryAndCallsGoogle(){UUID slot=agenda.create(admin,start,start.plusSeconds(1800)),key=UUID.randomUUID();agenda.reserve(key,slot,contact());agenda.cancel(admin,slot);agenda.cancel(admin,slot);verify(calendar,times(1)).cancel(key);assertEquals("CANCELLED",db.queryForObject("SELECT state FROM appointment_slot WHERE id=?",String.class,slot));assertEquals(1,db.queryForObject("SELECT count(*) FROM commercial_request",Integer.class));}
 @Test void whatsappFlowRequiresConsentAndDeduplicates(){
  agenda.create(admin,start,start.plusSeconds(1800));String sender="593999999999";int n=0;for(String message:List.of("CITAS","1","1","ACEPTO","Test Person","Fictional club","Ecuador","test@example.org","CONFIRMAR")){conversations.receive("test-"+(++n),sender,message,Instant.now(),"https://example.org");}
  conversations.receive("test-9",sender,"CONFIRMAR",Instant.now(),"https://example.org");assertEquals(1,db.queryForObject("SELECT count(*) FROM commercial_request",Integer.class));assertEquals(9,db.queryForObject("SELECT count(*) FROM whatsapp_reply",Integer.class));assertEquals(0,db.queryForObject("SELECT count(*) FROM whatsapp_session",Integer.class));assertFalse(db.queryForObject("SELECT payload FROM whatsapp_reply WHERE message_id='test-8'",String.class).contains("test@example.org"));
 }
 @Test void unsignedWebhookAndUnknownQuestionsAreSafe()throws Exception{
  assertFalse(WhatsAppGateway.validSignature("secret","{}".getBytes(),"sha256="+"0".repeat(64)));
  mvc.perform(post("/api/whatsapp/webhook").contentType("application/json").content("{}")).andExpect(status().isNotFound());
  mvc.perform(post("/api/v1/assistant/message").contentType("application/json").content("{\"message\":\"invented guaranteed discount\",\"locale\":\"en\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.matches").isEmpty());
 }
 @Test void generatedSlotsRespectEveryDayScheduleAndAreNotDuplicated(){int count=agenda.generate(admin);assertTrue(count>150);assertEquals(0,agenda.generate(admin));for(var slot:agenda.list(admin)){assertTrue(settings.permits(slot.start(),slot.end()));assertEquals(30,Duration.between(slot.start(),slot.end()).toMinutes());}}
}
