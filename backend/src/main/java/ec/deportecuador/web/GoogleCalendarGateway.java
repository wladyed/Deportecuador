package ec.deportecuador.web;

import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
public class GoogleCalendarGateway {
 private final ObjectMapper json;private final AssistantSettings settings;
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
 @Value("${app.calendar.client-id:}") String clientId;
 @Value("${app.calendar.client-secret:}") String clientSecret;
 @Value("${app.calendar.refresh-token:}") String refreshToken;
 GoogleCalendarGateway(ObjectMapper json,AssistantSettings settings){this.json=json;this.settings=settings;}
 public boolean ready(){var s=settings.read();return s.calendarEnabled()&&!s.calendarId().isBlank()&&!clientId.isBlank()&&!clientSecret.isBlank()&&!refreshToken.isBlank();}
 private String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
 private String token()throws Exception{
  if(!ready())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Google Calendar todavía no está conectado.");
  var request=HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token")).timeout(Duration.ofSeconds(15)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("grant_type=refresh_token&client_id="+encode(clientId)+"&client_secret="+encode(clientSecret)+"&refresh_token="+encode(refreshToken))).build();var r=client.send(request,HttpResponse.BodyHandlers.ofString());if(r.statusCode()!=200)throw new IllegalStateException();String token=json.readTree(r.body()).path("access_token").asText();if(token.isBlank())throw new IllegalStateException();return token;
 }
 private HttpResponse<String> call(String method,String path,Object body,String token)throws Exception{
  var b=HttpRequest.newBuilder(URI.create("https://www.googleapis.com/calendar/v3"+path)).timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+token).header("Content-Type","application/json");return client.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
 }
 private String path(){return "/calendars/"+encode(settings.read().calendarId())+"/events";}
 private List<Instant[]> busy(Instant start,Instant end,String token)throws Exception{
  String calendar=settings.read().calendarId();var r=call("POST","/freeBusy",Map.of("timeMin",start.toString(),"timeMax",end.toString(),"items",List.of(Map.of("id",calendar))),token);if(r.statusCode()!=200)throw new IllegalStateException();var data=json.readTree(r.body()).path("calendars").path(calendar);if(!data.has("busy")||data.has("errors"))throw new IllegalStateException();List<Instant[]> result=new ArrayList<>();for(var range:data.path("busy"))result.add(new Instant[]{Instant.parse(range.path("start").asText()),Instant.parse(range.path("end").asText())});return result;
 }
 public List<AppointmentService.Slot> available(List<AppointmentService.Slot> slots){
  if(!ready()||slots.isEmpty())return List.of();try{var ranges=busy(slots.get(0).start(),slots.stream().map(AppointmentService.Slot::end).max(Comparator.naturalOrder()).orElseThrow(),token());return slots.stream().filter(s->ranges.stream().noneMatch(b->s.start().isBefore(b[1])&&s.end().isAfter(b[0]))).toList();}catch(Exception e){throw unavailable();}
 }
 public void book(UUID key,AppointmentService.Slot slot){
  try{String token=token(),id="de"+key.toString().replace("-","");var existing=call("GET",path()+"/"+id,null,token);
   if(existing.statusCode()==200){var e=json.readTree(existing.body());if(e.path("status").asText().equals("cancelled")||!e.path("extendedProperties").path("private").path("slotId").asText().equals(slot.id().toString())||!Instant.parse(e.path("start").path("dateTime").asText()).equals(slot.start())||!Instant.parse(e.path("end").path("dateTime").asText()).equals(slot.end()))throw new ResponseStatusException(HttpStatus.CONFLICT);return;}
   if(existing.statusCode()!=404)throw new IllegalStateException();if(!busy(slot.start(),slot.end(),token).isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"El horario está ocupado en Google Calendar.");
   var body=Map.of("id",id,"summary","DeportEcuador · Demostración / Reunión","start",Map.of("dateTime",slot.start().toString(),"timeZone","America/Guayaquil"),"end",Map.of("dateTime",slot.end().toString(),"timeZone","America/Guayaquil"),"extendedProperties",Map.of("private",Map.of("slotId",slot.id().toString())),"description","Reserva DeportEcuador. Consulta los datos de contacto en el CRM. Código de reserva: "+key);
   var result=call("POST",path()+"?sendUpdates=none",body,token);if(result.statusCode()!=200&&result.statusCode()!=201)throw new IllegalStateException();
  }catch(ResponseStatusException e){throw e;}catch(Exception e){throw unavailable();}
 }
 public void cancel(UUID key){try{String token=token();var r=call("DELETE",path()+"/de"+key.toString().replace("-","")+"?sendUpdates=none",null,token);if(r.statusCode()!=204&&r.statusCode()!=404&&r.statusCode()!=410)throw new IllegalStateException();}catch(ResponseStatusException e){throw e;}catch(Exception e){throw unavailable();}}
 private ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"No se pudo verificar Google Calendar. No se confirma la reserva; inténtalo de nuevo.");}
}
