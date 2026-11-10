package ec.deportecuador.web.admin;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class AccountRecovery {
 private final JdbcTemplate db;private final AdminAccounts accounts;private final AdminSecrets secrets;private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(12);
 @Value("${app.auth.public-origin:http://localhost:8081}") String publicOrigin;
 public AccountRecovery(JdbcTemplate db,AdminAccounts accounts,AdminSecrets secrets){this.db=db;this.accounts=accounts;this.secrets=secrets;}
 private void lock(UUID id){if(db.queryForList("SELECT id FROM admin_user WHERE id=? AND enabled=TRUE AND totp_secret IS NOT NULL FOR UPDATE",UUID.class,id).isEmpty())throw invalid();}
 @Transactional public void requestPassword(String email){
  var ids=db.queryForList("SELECT id FROM admin_user WHERE email=? AND enabled=TRUE AND totp_secret IS NOT NULL",UUID.class,email.trim().toLowerCase(Locale.ROOT));if(ids.isEmpty())return;UUID id=ids.get(0);lock(id);
  if(db.queryForObject("SELECT count(*) FROM admin_password_reset WHERE user_id=? AND created_at>?",Integer.class,id,Timestamp.from(Instant.now().minusSeconds(300)))>0)return;
  db.update("UPDATE admin_password_reset SET used_at=CURRENT_TIMESTAMP WHERE user_id=? AND used_at IS NULL",id);
  String token=AdminSecrets.token(),hash=AdminSecrets.hash(token);
  db.update("INSERT INTO admin_password_reset(token_hash,user_id,expires_at) VALUES(?,?,?)",hash,id,Timestamp.from(Instant.now().plusSeconds(1800)));
  String origin=publicOrigin.replaceAll("/+$","");if(!origin.matches("https://[a-zA-Z0-9.-]+(?::[0-9]+)?|http://(?:localhost|127\\.0\\.0\\.1)(?::[0-9]+)?"))throw new IllegalStateException("Invalid recovery origin");
  String body="Solicitaste restablecer tu contraseña de DeportEcuador. Abre este enlace en el mismo navegador que utilizas para el panel:\n"+origin+"/admin/#reset="+java.net.URLEncoder.encode(token,StandardCharsets.UTF_8)+"\nVence en 30 minutos y solo se puede utilizar una vez. El segundo factor se mantiene. Si no lo solicitaste, ignora este mensaje.";
  db.update("INSERT INTO admin_auth_mail(id,token_hash,payload) VALUES(?,?,?)",UUID.randomUUID(),hash,secrets.encrypt(body));accounts.audit(id,"PASSWORD_RESET_REQUESTED",id);
 }
 @Transactional public void resetPassword(String token,String password){
  if(password.length()<12||password.getBytes(StandardCharsets.UTF_8).length>72)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La contraseña requiere al menos 12 caracteres y como máximo 72 bytes.");
  String hash=AdminSecrets.hash(token);var ids=db.queryForList("SELECT user_id FROM admin_password_reset WHERE token_hash=? AND used_at IS NULL AND expires_at>CURRENT_TIMESTAMP",UUID.class,hash);if(ids.isEmpty())throw invalid();UUID id=ids.get(0);lock(id);
  if(db.update("UPDATE admin_password_reset SET used_at=CURRENT_TIMESTAMP WHERE token_hash=? AND used_at IS NULL AND expires_at>CURRENT_TIMESTAMP",hash)!=1)throw invalid();
  db.update("UPDATE admin_user SET password_hash=?,auth_version=auth_version+1,failed_attempts=0,locked_until=NULL WHERE id=?",passwords.encode(password),id);db.update("UPDATE admin_password_reset SET used_at=CURRENT_TIMESTAMP WHERE user_id=? AND used_at IS NULL",id);accounts.audit(id,"PASSWORD_RESET",id);
 }
 public boolean verify(AdminIdentity actor,String password,String code,boolean recentRecovery){UUID id=accounts.checkPassword(actor.email(),password);return actor.id().equals(id)&&(recentRecovery||accounts.checkFactor(id,code)!=null);}
 @Transactional public List<String> codes(AdminIdentity actor){lock(actor.id());db.update("DELETE FROM admin_recovery_code WHERE user_id=?",actor.id());var codes=new ArrayList<String>();for(int i=0;i<10;i++){String code=UUID.randomUUID().toString().replace("-","");db.update("INSERT INTO admin_recovery_code(code_hash,user_id) VALUES(?,?)",AdminSecrets.hash(code),actor.id());codes.add(code);}accounts.audit(actor.id(),"RECOVERY_CODES_GENERATED",actor.id());return codes;}
 public int remaining(UUID id){return db.queryForObject("SELECT count(*) FROM admin_recovery_code WHERE user_id=? AND used_at IS NULL",Integer.class,id);}
 @Transactional public boolean replaceMfa(AdminIdentity actor,long version,String secret,String code){lock(actor.id());if(accounts.authVersion(actor.id())!=version)throw invalid();long step=Totp.match(secret,code,Instant.now(),-1);if(step<0)return false;db.update("UPDATE admin_user SET totp_secret=?,last_totp_step=?,auth_version=auth_version+1,failed_attempts=0,locked_until=NULL WHERE id=?",secrets.encrypt(secret),step,actor.id());db.update("DELETE FROM admin_recovery_code WHERE user_id=?",actor.id());accounts.audit(actor.id(),"MFA_REPLACED",actor.id());return true;}
 static ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Código inválido, vencido o ya utilizado.");}
}
