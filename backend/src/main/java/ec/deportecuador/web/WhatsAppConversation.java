package ec.deportecuador.web;

import ec.deportecuador.web.admin.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;

/** Guided flow; all personal session data are encrypted and expire after 30 minutes. */
@Service
public class WhatsAppConversation {
 private final JdbcTemplate db;private final AdminSecrets secrets;private final ObjectMapper json;private final AppointmentService agenda;private final EditorialService content;private final AssistantController assistant;private final jakarta.validation.Validator validator;
 WhatsAppConversation(JdbcTemplate db,AdminSecrets secrets,ObjectMapper json,AppointmentService agenda,EditorialService content,AssistantController assistant,jakarta.validation.Validator validator){this.db=db;this.secrets=secrets;this.json=json;this.agenda=agenda;this.content=content;this.assistant=assistant;this.validator=validator;}
 @Transactional public void receive(String messageId,String sender,String message,Instant sentAt,String publicUrl){
  db.queryForObject("SELECT id FROM appointment_agenda WHERE id=1 FOR UPDATE",Integer.class);
  if(db.queryForObject("SELECT count(*) FROM whatsapp_reply WHERE message_id=?",Integer.class,messageId)>0)return;
  if(sentAt.isBefore(Instant.now().minusSeconds(23*3600))||sentAt.isAfter(Instant.now().plusSeconds(300)))return;
  String key=AdminSecrets.hash(sender);var rows=db.queryForList("SELECT payload FROM whatsapp_session WHERE sender_key=? AND expires_at>CURRENT_TIMESTAMP",String.class,key);
  Map<String,String> state=rows.isEmpty()?new LinkedHashMap<>():decode(secrets.decrypt(rows.get(0)));
  String reply=step(state,message,publicUrl,UUID.nameUUIDFromBytes(messageId.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  db.update("DELETE FROM whatsapp_session WHERE sender_key=?",key);
  if(!state.isEmpty())db.update("INSERT INTO whatsapp_session(sender_key,payload,expires_at) VALUES(?,?,?)",key,secrets.encrypt(encode(state)),Timestamp.from(Instant.now().plusSeconds(1800)));
  db.update("INSERT INTO whatsapp_reply(message_id,payload,expires_at) VALUES(?,?,?)",messageId,secrets.encrypt(encode(Map.of("to",sender,"text",reply))),Timestamp.from(sentAt.plusSeconds(23*3600)));
 }
 private String step(Map<String,String> s,String input,String url,UUID bookingKey){
  String text=input.trim();
  if(text.equalsIgnoreCase("CANCELAR")){s.clear();return "Proceso cancelado. No se ha creado una reserva. Escribe CITAS para comenzar o consulta un producto o servicio.";}
  if(text.equalsIgnoreCase("CITAS")){
   s.clear();var slots=agenda.available().stream().limit(10).toList();if(slots.isEmpty())return "No hay horarios publicados. Contacta con info@deportecuador.com.ec.";
   StringBuilder out=new StringBuilder("Soy el asistente automático de DeportEcuador. Horarios de Quito (America/Guayaquil):\n");int i=0;for(var slot:slots){s.put("slot"+(++i),slot.id().toString());out.append(i).append(". ").append(slot.start().atZone(ZoneId.of("America/Guayaquil")).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))).append(" ( ").append(Duration.between(slot.start(),slot.end()).toMinutes()).append(" min)\n");}s.put("stage","SLOT");return out+"Responde con el número del horario. No se bloquea hasta confirmar. CANCELAR termina el proceso.";
  }
  String stage=s.getOrDefault("stage","");
  switch(stage){
   case "SLOT": {
    if(!s.containsKey("slot"+text))return "Responde con un número de la lista o CANCELAR.";String id=s.get("slot"+text);s.clear();s.put("slotId",id);s.put("stage","TOPIC");StringBuilder out=new StringBuilder("Selecciona el tema:\n");int n=0;
    for(var e:content.published("es").entrySet()){if(!RequestService.PRODUCTS.contains(e.getKey())&&!RequestService.SERVICES.contains(e.getKey()))continue;s.put("topic"+(++n),e.getKey());out.append(n).append(". ").append(e.getValue().get("name")).append('\n');}return out+"Responde con el número del tema.";
   }
   case "TOPIC": if(!s.containsKey("topic"+text))return "Selecciona un número de tema válido.";s.put("interest",s.get("topic"+text));s.put("stage","CONSENT");return "Usaremos tu nombre, organización, país y correo para gestionar la cita. Aviso: "+url+"/es/privacy. Responde ACEPTO para proporcionar estos datos o CANCELAR. No envíes información sensible.";
   case "CONSENT": if(!text.equalsIgnoreCase("ACEPTO"))return "Responde ACEPTO o CANCELAR.";s.put("stage","NAME");return "¿Cuál es tu nombre? (hasta 180 caracteres)";
   case "NAME": if(text.isBlank()||text.length()>180)return "Escribe un nombre de hasta 180 caracteres.";s.put("name",text);s.put("stage","ORG");return "¿Cuál es tu organización?";
   case "ORG": if(text.isBlank()||text.length()>180)return "Escribe una organización de hasta 180 caracteres.";s.put("organization",text);s.put("stage","COUNTRY");return "¿En qué país estás?";
   case "COUNTRY": if(text.isBlank()||text.length()>100)return "Escribe un país de hasta 100 caracteres.";s.put("country",text);s.put("stage","EMAIL");return "¿Cuál es tu correo electrónico?";
   case "EMAIL": {
    s.put("email",text);if(!validator.validate(intake(s)).isEmpty())return "Revisa el correo electrónico (máximo 254 caracteres).";s.put("stage","CONFIRM");var slot=agenda.available().stream().filter(v->v.id().toString().equals(s.get("slotId"))).findFirst();if(slot.isEmpty()){s.clear();return "El horario ya no está disponible. Escribe CITAS para elegir otro.";}
    return "Revisa tu reserva: "+slot.get().start().atZone(ZoneId.of("America/Guayaquil")).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))+" (Quito), "+s.get("interest")+".\n"+s.get("name")+" · "+s.get("organization")+" · "+s.get("country")+" · "+text+".\nResponde CONFIRMAR para reservar o CANCELAR para comenzar de nuevo.";
   }
   case "CONFIRM": {
    if(!text.equalsIgnoreCase("CONFIRMAR"))return "Responde CONFIRMAR o CANCELAR.";
    if(agenda.eligible().stream().noneMatch(v->v.id().toString().equals(s.get("slotId")))||!content.published("es").containsKey(s.get("interest"))){s.clear();return "El horario o tema ya no está disponible. Escribe CITAS para comenzar de nuevo.";}
    var receipt=agenda.reserve(bookingKey,UUID.fromString(s.get("slotId")),intake(s));s.clear();return "Cita reservada en Google Calendar. Referencia: "+receipt.get("id")+". Recibirás la confirmación por correo. Para cambios: info@deportecuador.com.ec.";
   }
   default: {
    var answer=assistant.reply(text,"es");@SuppressWarnings("unchecked") var matches=(List<Map<String,String>>)answer.get("matches");return (matches.isEmpty()?answer.get("message").toString():matches.stream().map(m->m.get("name")+"\n"+m.get("description")).reduce((a,b)->a+"\n\n"+b).orElse(""))+"\nEscribe CITAS para reservar una demostración o reunión. Atención humana: info@deportecuador.com.ec.";
   }
  }
 }
 private Intake intake(Map<String,String> s){return new Intake(RequestService.PRODUCTS.contains(s.get("interest"))?"DEMO":"ADVICE",s.get("organization"),s.get("country"),s.get("name"),s.get("email"),"","Cita desde WhatsApp",List.of(s.get("interest")),"es",true,"draft-2026-10-19","");}
 private String encode(Map<String,String> value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Unable to encode conversation");}}
 Map<String,String> decode(String value){try{return json.readValue(value,new TypeReference<LinkedHashMap<String,String>>(){});}catch(Exception e){throw new IllegalStateException("Unable to decode conversation");}}
}
