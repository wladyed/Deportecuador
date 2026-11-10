package ec.deportecuador.web;

import ec.deportecuador.web.admin.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import java.time.Instant;
import java.util.*;

@RestController
public class AssistantController {
 private final AppointmentService agenda;private final EditorialService editorial;private final AssistantSettings settings;private final GoogleCalendarGateway calendar;
 AssistantController(AppointmentService agenda,EditorialService editorial,AssistantSettings settings,GoogleCalendarGateway calendar){this.agenda=agenda;this.editorial=editorial;this.settings=settings;this.calendar=calendar;}
 public record Question(@NotBlank @Size(max=500) String message,@Pattern(regexp="es|en|pt") @NotNull String locale){}
 public record Booking(@NotNull UUID slotId,@NotNull @Valid Intake contact){}
 public record Availability(@NotNull Instant start,@NotNull Instant end){}
 @GetMapping("/api/v1/assistant/config") public Map<String,Object> config(){var s=settings.read();return Map.of("whatsapp",s.whatsappLinkEnabled()?"https://wa.me/"+s.whatsappPhone():"","calendarReady",calendar.ready());}
 @GetMapping("/api/admin/assistant-settings") public Map<String,Object> settings(@AuthenticationPrincipal AdminIdentity actor){AdminAccounts.requireAdmin(actor);return Map.of("settings",settings.read(),"calendarReady",calendar.ready());}
 @PutMapping("/api/admin/assistant-settings") @ResponseStatus(HttpStatus.NO_CONTENT) public void saveSettings(@AuthenticationPrincipal AdminIdentity actor,@Valid @RequestBody AssistantSettings.Settings value){settings.save(actor,value);}
 @PostMapping("/api/v1/assistant/message") public Map<String,Object> answer(@Valid @RequestBody Question question){return reply(question.message(),question.locale());}
 public Map<String,Object> reply(String message,String lang){
  var docs=editorial.published(lang);String q=normalize(message);
  var matches=docs.entrySet().stream().filter(e->e.getValue().containsKey("name")&&e.getValue().containsKey("description")).filter(e->{String title=normalize(e.getValue().get("name"));return q.contains(e.getKey())||Arrays.stream(q.split("\\s+")).filter(w->w.length()>3).anyMatch(title::contains);}).limit(3).map(e->Map.of("name",e.getValue().get("name"),"description",e.getValue().get("description"))).toList();
  String response=switch(lang){case "en"->"I am DeportEcuador's automated assistant. I can show published product and service information and help book an available appointment. Select a topic or open the appointment calendar. For other questions: info@deportecuador.com.ec.";case "pt"->"Sou o assistente automático da DeportEcuador. Posso mostrar informações publicadas de produtos e serviços e ajudar a reservar um horário disponível. Escolha um tema ou abra a agenda. Para outras perguntas: info@deportecuador.com.ec.";default->"Soy el asistente automático de DeportEcuador. Puedo mostrar información publicada de productos y servicios y ayudarte a reservar un horario disponible. Elige un tema o abre la agenda. Para otras consultas: info@deportecuador.com.ec.";};
  return Map.of("message",response,"matches",matches);
 }
 private String normalize(String s){return java.text.Normalizer.normalize(s.toLowerCase(Locale.ROOT),java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","");}
 @GetMapping("/api/v1/assistant/slots") public ResponseEntity<List<Map<String,Object>>> slots(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(agenda.available().stream().map(s->Map.<String,Object>of("id",s.id(),"start",s.start(),"end",s.end())).toList());}
 @PostMapping("/api/v1/assistant/bookings") public Map<String,Object> book(@RequestHeader("Idempotency-Key") UUID key,@Valid @RequestBody Booking input){return agenda.reserve(key,input.slotId(),input.contact());}
 @GetMapping("/api/admin/appointments") public List<AppointmentService.Slot> list(@AuthenticationPrincipal AdminIdentity actor){return agenda.list(actor);}
 @PostMapping("/api/admin/appointments/generate") public Map<String,Integer> generate(@AuthenticationPrincipal AdminIdentity actor){return Map.of("created",agenda.generate(actor));}
 @PostMapping("/api/admin/appointments") public Map<String,UUID> create(@AuthenticationPrincipal AdminIdentity actor,@Valid @RequestBody Availability input){return Map.of("id",agenda.create(actor,input.start(),input.end()));}
 @PostMapping("/api/admin/appointments/{id}/cancel") @ResponseStatus(HttpStatus.NO_CONTENT) public void cancel(@AuthenticationPrincipal AdminIdentity actor,@PathVariable UUID id){agenda.cancel(actor,id);}
}
