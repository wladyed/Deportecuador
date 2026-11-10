package ec.deportecuador.web;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mail.javamail.*;
import org.springframework.beans.factory.annotation.Value;
import java.time.Instant; import java.util.UUID;
@Component class Notifications {
 private final JdbcTemplate jdbc;private final JavaMailSender mail;private final boolean enabled;private final String from;private final RequestService requests;private final String admin;
 Notifications(JdbcTemplate j,JavaMailSender m,RequestService r,@Value("${app.mail.enabled}")boolean e,@Value("${app.mail.from}")String f,@Value("${app.mail.admin}")String a){jdbc=j;mail=m;requests=r;enabled=e;from=f;admin=a;}
 @Scheduled(fixedDelay=15000) void deliver(){if(!enabled)return;
  for(var row:jdbc.queryForList("SELECT * FROM notification_outbox WHERE sent_at IS NULL AND next_attempt_at<=CURRENT_TIMESTAMP ORDER BY created_at LIMIT 20")){
   try{var message=mail.createMimeMessage();var helper=new MimeMessageHelper(message,"UTF-8");helper.setFrom(from,"Info DeportEcuador");helper.setTo((String)row.get("recipient"));helper.setSubject((String)row.get("subject"));helper.setText((String)row.get("body"),false);mail.send(message);jdbc.update("UPDATE notification_outbox SET sent_at=CURRENT_TIMESTAMP WHERE id=?",row.get("id"));}
   catch(Exception ex){int attempts=((Number)row.get("attempts")).intValue()+1;jdbc.update("UPDATE notification_outbox SET attempts=?, next_attempt_at=? WHERE id=?",attempts,java.sql.Timestamp.from(Instant.now().plusSeconds(Math.min(3600,60L*attempts))),row.get("id"));}
  }
 }
 @Scheduled(fixedDelay=60000) @Transactional public void alertUnassigned(){
  for(var row:jdbc.queryForList("SELECT id FROM commercial_request WHERE assigned_to IS NULL AND status NOT IN ('WON','LOST') AND unassigned_alerted=FALSE AND created_at<?",java.sql.Timestamp.from(Instant.now().minusSeconds(86400)))){
   UUID id=(UUID)row.get("id");requests.event(id,"UNASSIGNED_24H",admin,"Solicitud sin asignar","La solicitud "+id+" permanece sin responsable después de 24 horas.");jdbc.update("UPDATE commercial_request SET unassigned_alerted=TRUE WHERE id=?",id);
  }
 }
}
