package ec.deportecuador.web;

import ec.deportecuador.web.admin.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

@Service
public class AssistantSettings {
 private final JdbcTemplate db;private final AdminAccounts accounts;
 AssistantSettings(JdbcTemplate db,AdminAccounts accounts){this.db=db;this.accounts=accounts;}
 public record Settings(@NotNull @Pattern(regexp="[1-9][0-9]{7,14}")String whatsappPhone,boolean whatsappLinkEnabled,@NotNull @Size(max=254)String calendarId,boolean calendarEnabled,@NotNull @Pattern(regexp="[0-2][0-9]:[0-5][0-9]")String opensAt,@NotNull @Pattern(regexp="[0-2][0-9]:[0-5][0-9]")String closesAt,@Min(15) @Max(180)int durationMinutes,@NotNull @Pattern(regexp="[1-7](,[1-7]){0,6}")String weekdays){}
 public Settings read(){return db.queryForObject("SELECT * FROM assistant_settings WHERE id=1",(r,n)->new Settings(r.getString("whatsapp_phone"),r.getBoolean("whatsapp_link_enabled"),r.getString("calendar_id"),r.getBoolean("calendar_enabled"),r.getString("opens_at"),r.getString("closes_at"),r.getInt("duration_minutes"),r.getString("weekdays")));}
 @Transactional public void save(AdminIdentity actor,Settings s){AdminAccounts.requireAdmin(actor);db.queryForObject("SELECT id FROM appointment_agenda WHERE id=1 FOR UPDATE",Integer.class);
  try{if(!LocalTime.parse(s.opensAt()).isBefore(LocalTime.parse(s.closesAt()))||Duration.between(LocalTime.parse(s.opensAt()),LocalTime.parse(s.closesAt())).toMinutes()<s.durationMinutes())throw new IllegalArgumentException();}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Revisa el horario de atención.");}
  if(s.calendarEnabled()&&s.calendarId().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Indica el identificador de Google Calendar.");
  Settings previous=read();if(!previous.calendarId().equals(s.calendarId())&&db.queryForObject("SELECT count(*) FROM appointment_slot WHERE state='BOOKED' AND ends_at>CURRENT_TIMESTAMP",Integer.class)>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"No cambies de calendario mientras existan reservas futuras.");
  db.update("UPDATE assistant_settings SET whatsapp_phone=?,whatsapp_link_enabled=?,calendar_id=?,calendar_enabled=?,opens_at=?,closes_at=?,duration_minutes=?,weekdays=? WHERE id=1",s.whatsappPhone(),s.whatsappLinkEnabled(),s.calendarId().trim(),s.calendarEnabled(),s.opensAt(),s.closesAt(),s.durationMinutes(),s.weekdays());accounts.audit(actor.id(),"ASSISTANT_SETTINGS_CHANGED",null);
 }
 public boolean permits(Instant start,Instant end){var s=read();var a=start.atZone(ZoneId.of("America/Guayaquil"));var b=end.atZone(ZoneId.of("America/Guayaquil"));return a.toLocalDate().equals(b.toLocalDate())&&Arrays.asList(s.weekdays().split(",")).contains(""+a.getDayOfWeek().getValue())&&!a.toLocalTime().isBefore(LocalTime.parse(s.opensAt()))&&!b.toLocalTime().isAfter(LocalTime.parse(s.closesAt()))&&Duration.between(start,end).equals(Duration.ofMinutes(s.durationMinutes()));}
}
