package ec.deportecuador.web.admin;

import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.*;
import org.springframework.beans.factory.annotation.Value;
import java.time.Instant;
import java.sql.Timestamp;

@Component
public class AuthMail {
 private final JdbcTemplate db;private final AdminSecrets secrets;private final JavaMailSender mail;
 @Value("${app.mail.enabled}") boolean enabled;@Value("${app.mail.from}") String from;
 public AuthMail(JdbcTemplate db,AdminSecrets secrets,JavaMailSender mail){this.db=db;this.secrets=secrets;this.mail=mail;}
 @Scheduled(fixedDelay=15000) public void deliver(){
  if(!enabled)return;
  for(var r:db.queryForList("SELECT m.id,m.payload,m.attempts,u.email FROM admin_auth_mail m JOIN admin_password_reset p ON p.token_hash=m.token_hash JOIN admin_user u ON u.id=p.user_id WHERE m.sent_at IS NULL AND m.next_attempt_at<=CURRENT_TIMESTAMP AND p.used_at IS NULL AND p.expires_at>CURRENT_TIMESTAMP AND u.enabled=TRUE ORDER BY m.next_attempt_at LIMIT 10")){
   try{var message=mail.createMimeMessage();var helper=new MimeMessageHelper(message,"UTF-8");helper.setFrom(from);helper.setTo((String)r.get("email"));helper.setSubject("DeportEcuador · Recuperación de contraseña");helper.setText(secrets.decrypt((String)r.get("payload")),false);mail.send(message);db.update("UPDATE admin_auth_mail SET sent_at=CURRENT_TIMESTAMP,payload='' WHERE id=?",r.get("id"));}
   catch(Exception e){int attempts=((Number)r.get("attempts")).intValue()+1;db.update("UPDATE admin_auth_mail SET attempts=?,next_attempt_at=? WHERE id=?",attempts,Timestamp.from(Instant.now().plusSeconds(Math.min(300,30L*attempts))),r.get("id"));}
  }
  db.update("UPDATE admin_auth_mail SET payload='' WHERE token_hash IN (SELECT token_hash FROM admin_password_reset WHERE used_at IS NOT NULL OR expires_at<CURRENT_TIMESTAMP)");
 }
}
