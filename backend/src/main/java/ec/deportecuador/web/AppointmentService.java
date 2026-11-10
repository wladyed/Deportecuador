package ec.deportecuador.web;

import ec.deportecuador.web.admin.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class AppointmentService {
 private final JdbcTemplate db; private final RequestService requests; private final AdminAccounts accounts;private final GoogleCalendarGateway calendar;private final AssistantSettings settings;private final EditorialService editorial;
 AppointmentService(JdbcTemplate db,RequestService requests,AdminAccounts accounts,GoogleCalendarGateway calendar,AssistantSettings settings,EditorialService editorial){this.db=db;this.requests=requests;this.accounts=accounts;this.calendar=calendar;this.settings=settings;this.editorial=editorial;}
 public record Slot(UUID id,Instant start,Instant end,String state,UUID requestId){}
 private final org.springframework.jdbc.core.RowMapper<Slot> mapper=(r,n)->new Slot(r.getObject("id",UUID.class),r.getTimestamp("starts_at").toInstant(),r.getTimestamp("ends_at").toInstant(),r.getString("state"),r.getObject("request_id",UUID.class));
 private void lock(){db.queryForObject("SELECT id FROM appointment_agenda WHERE id=1 FOR UPDATE",Integer.class);}
 public List<Slot> eligible(){return db.query("SELECT * FROM appointment_slot WHERE state='OPEN' AND starts_at>? ORDER BY starts_at LIMIT 60",mapper,Timestamp.from(Instant.now().plusSeconds(3600))).stream().filter(s->settings.permits(s.start(),s.end())).toList();}
 public List<Slot> available(){return calendar.available(eligible());}
 public List<Slot> list(AdminIdentity actor){AdminAccounts.requireAdmin(actor);return db.query("SELECT * FROM appointment_slot ORDER BY starts_at DESC LIMIT 200",mapper);}
 @Transactional public int generate(AdminIdentity actor){
  AdminAccounts.requireAdmin(actor);lock();var config=settings.read();int count=0;var zone=ZoneId.of("America/Guayaquil");var today=LocalDate.now(zone);
  for(int day=0;day<14;day++){var date=today.plusDays(day);for(var at=LocalTime.parse(config.opensAt());!at.plusMinutes(config.durationMinutes()).isAfter(LocalTime.parse(config.closesAt()))&&at.plusMinutes(config.durationMinutes()).isAfter(at);at=at.plusMinutes(config.durationMinutes())){
   var start=date.atTime(at).atZone(zone).toInstant();var end=start.plusSeconds(config.durationMinutes()*60L);if(start.isBefore(Instant.now().plusSeconds(3600))||!settings.permits(start,end))continue;
   if(db.queryForObject("SELECT count(*) FROM appointment_slot WHERE starts_at<? AND ends_at>?",Integer.class,Timestamp.from(end),Timestamp.from(start))>0)continue;
   create(actor,start,end);count++;
  }}return count;
 }
 @Transactional public UUID create(AdminIdentity actor,Instant start,Instant end){
  AdminAccounts.requireAdmin(actor);lock();
  if(start==null||end==null||!settings.permits(start,end))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El horario debe respetar los días, duración y horas configurados.");
  if(start==null||end==null||start.isBefore(Instant.now().plusSeconds(3600))||start.isAfter(Instant.now().plusSeconds(86400L*180))||Duration.between(start,end).toMinutes()<15||Duration.between(start,end).toMinutes()>180)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Usa un horario futuro de 15 a 180 minutos, con una hora de anticipación y dentro de los próximos 180 días.");
  if(db.queryForObject("SELECT count(*) FROM appointment_slot WHERE state<>'CANCELLED' AND starts_at<? AND ends_at>?",Integer.class,Timestamp.from(end),Timestamp.from(start))>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"El horario se solapa con otro existente.");
  UUID id=UUID.randomUUID();db.update("INSERT INTO appointment_slot(id,starts_at,ends_at) VALUES(?,?,?)",id,Timestamp.from(start),Timestamp.from(end));accounts.audit(actor.id(),"APPOINTMENT_SLOT_CREATED",id);return id;
 }
 @Transactional public void cancel(AdminIdentity actor,UUID id){
  AdminAccounts.requireAdmin(actor);lock();var rows=db.query("SELECT * FROM appointment_slot WHERE id=?",mapper,id);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);var slot=rows.get(0);
  if(slot.state().equals("CANCELLED"))return;
  if(slot.requestId()!=null)calendar.cancel(db.queryForObject("SELECT booking_key FROM appointment_slot WHERE id=?",UUID.class,id));
  db.update("UPDATE appointment_slot SET state='CANCELLED' WHERE id=?",id);accounts.audit(actor.id(),"APPOINTMENT_CANCELLED",id);
  if(slot.requestId()!=null){String email=db.queryForObject("SELECT email FROM commercial_request WHERE id=?",String.class,slot.requestId());requests.event(slot.requestId(),"APPOINTMENT_CANCELLED",email,"DeportEcuador · Cita cancelada / Appointment cancelled / Reunião cancelada",slot.start()+" UTC. Referencia / Reference: "+slot.requestId()+". info@deportecuador.com.ec");}
 }
 @Transactional public Map<String,Object> reserve(UUID key,UUID slotId,Intake input){
  lock();var previous=db.query("SELECT * FROM appointment_slot WHERE booking_key=?",mapper,key);
  if(!previous.isEmpty()){var s=previous.get(0);if(!s.id().equals(slotId))throw new ResponseStatusException(HttpStatus.CONFLICT);return receipt(s);}
  var rows=db.query("SELECT * FROM appointment_slot WHERE id=?",mapper,slotId);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);var s=rows.get(0);
  if(!s.state().equals("OPEN")||s.start().isBefore(Instant.now().plusSeconds(3600))||!settings.permits(s.start(),s.end()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Ese horario ya no está disponible.");
  if(!(input.type().equals("DEMO")?RequestService.PRODUCTS:RequestService.SERVICES).containsAll(input.interests())||new HashSet<>(input.interests()).size()!=input.interests().size()||(!input.type().equals("DEMO")&&(input.description()==null||input.description().isBlank())))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  if(!editorial.published(input.locale()).keySet().containsAll(input.interests()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El tema ya no está publicado.");
  calendar.book(key,s);
  UUID request=requests.receive(UUID.randomUUID(),input);
  db.update("UPDATE appointment_slot SET state='BOOKED',request_id=?,booking_key=? WHERE id=?",request,key,slotId);
  requests.event(request,"APPOINTMENT_BOOKED",input.email(),"DeportEcuador · Cita reservada / Appointment booked / Reunião reservada",s.start()+" UTC — "+s.end()+" UTC. Referencia / Reference: "+request+". info@deportecuador.com.ec");
  return receipt(new Slot(s.id(),s.start(),s.end(),"BOOKED",request));
 }
 private Map<String,Object> receipt(Slot s){return Map.of("id",s.requestId(),"start",s.start(),"end",s.end(),"status",s.state());}
}
