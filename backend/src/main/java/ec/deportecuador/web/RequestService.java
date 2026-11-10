package ec.deportecuador.web;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant; import java.util.*;
@Service class RequestService {
 private final RequestRepository requests;private final JdbcTemplate jdbc;private final String admin;
 static final Set<String> PRODUCTS=Set.of("gestion-integral","competencias","asistodep","juridica-transparencia");
 static final Set<String> SERVICES=Set.of("consultoria","incentivo-tributario","importacion","soporte","asesoria-juridica","implementacion");
 RequestService(RequestRepository requests,JdbcTemplate jdbc,@Value("${app.mail.admin}")String admin){this.requests=requests;this.jdbc=jdbc;this.admin=admin;}
 @Transactional UUID receive(UUID key,Intake in){
  var old=requests.findByRequestKey(key); if(old.isPresent())return old.get().id;
  if(!(in.type().equals("DEMO")?PRODUCTS:SERVICES).containsAll(in.interests()) || new HashSet<>(in.interests()).size()!=in.interests().size() || (in.type().equals("ADVICE")&&(in.description()==null||in.description().isBlank())))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  var r=new CommercialRequest();r.id=UUID.randomUUID();r.requestKey=key;r.requestType=in.type();r.organization=in.organization().trim();r.country=in.country().trim();r.contactName=in.name().trim();r.email=in.email().trim();r.phone=in.phone()==null?"":in.phone().trim();r.description=in.description()==null?"":in.description().trim();r.interests=String.join(",",in.interests());r.locale=in.locale();r.policyVersion=in.policyVersion();r.createdAt=Instant.now();r.consentAt=r.createdAt;r.status="NEW";
  requests.saveAndFlush(r);
  event(r.id,"NEW_REQUEST",admin,"Nueva solicitud DeportEcuador","Solicitud "+r.id+" recibida. Consulte el registro mediante acceso autorizado. No responda este mensaje con datos personales.");
  String subject=switch(in.locale()){case "en"->"We received your request";case "pt"->"Recebemos sua solicitação";default->"Recibimos tu solicitud";};
  String body=switch(in.locale()){case "en"->"Your request has been registered. Reference: ";case "pt"->"Sua solicitação foi registrada. Referência: ";default->"Tu solicitud fue registrada. Referencia: ";};
  event(r.id,"REQUEST_RECEIVED",r.email,subject,body+r.id+". DeportEcuador · info@deportecuador.com.ec");return r.id;
 }
 void event(UUID id,String type,String to,String subject,String body){Instant now=Instant.now();jdbc.update("INSERT INTO notification_outbox(id,request_id,event_type,recipient,subject,body,created_at,attempts,next_attempt_at) VALUES(?,?,?,?,?,?,?,0,?)",UUID.randomUUID(),id,type,to,subject,body,java.sql.Timestamp.from(now),java.sql.Timestamp.from(now));}
}
