package ec.deportecuador.web.admin;

import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.*;
import java.time.Instant;
import java.sql.Timestamp;

@Component
public class CrmNotifications {
    private final CrmService crm; private final JdbcTemplate db; private final JavaMailSender mail;
    private final boolean enabled; private final String from;
    public CrmNotifications(CrmService crm,JdbcTemplate db,JavaMailSender mail,@Value("${app.mail.enabled}") boolean enabled,@Value("${app.mail.from}") String from) {
        this.crm=crm;this.db=db;this.mail=mail;this.enabled=enabled;this.from=from;
    }
    @Scheduled(fixedDelay=60000) public void schedule(){crm.scheduleDue();}
    @Scheduled(fixedDelay=15000) public void deliver(){
        if(!enabled)return;
        for(var row:db.queryForList("SELECT n.*,u.email FROM crm_notice n JOIN commercial_request r ON r.id=n.request_id JOIN admin_user u ON u.id=n.recipient_id WHERE n.sent_at IS NULL AND n.cancelled=FALSE AND n.next_attempt_at<=CURRENT_TIMESTAMP AND r.assigned_to=n.recipient_id AND r.status NOT IN ('WON','LOST') AND u.enabled=TRUE AND u.role IN ('ADMIN','COMMERCIAL') ORDER BY n.created_at LIMIT 20")) {
            try {
                var message=mail.createMimeMessage();var helper=new MimeMessageHelper(message,"UTF-8");
                helper.setFrom(from,"Info DeportEcuador");helper.setTo((String)row.get("email"));
                helper.setSubject("ASSIGNMENT".equals(row.get("kind"))?"Solicitud asignada — DeportEcuador":"Seguimiento pendiente — DeportEcuador");
                helper.setText("Tienes una notificación comercial en el panel de DeportEcuador. Accede con tu cuenta autorizada para consultar tus solicitudes.",false);
                mail.send(message);db.update("UPDATE crm_notice SET sent_at=CURRENT_TIMESTAMP WHERE id=?",row.get("id"));
            } catch(Exception ex) {
                int attempts=((Number)row.get("attempts")).intValue()+1;
                db.update("UPDATE crm_notice SET attempts=?,next_attempt_at=? WHERE id=?",attempts,Timestamp.from(Instant.now().plusSeconds(Math.min(3600,60L*attempts))),row.get("id"));
            }
        }
    }
}
