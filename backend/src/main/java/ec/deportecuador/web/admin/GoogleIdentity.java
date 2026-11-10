package ec.deportecuador.web.admin;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import java.util.*;

/** Claims are accepted only after Spring's OIDC signature, issuer, audience and nonce validation. */
@Service
public class GoogleIdentity {
 private final JdbcTemplate db;private final AdminAccounts accounts;
 @Value("${app.auth.google.enabled:false}") boolean enabled;
 @Value("${app.auth.google.domain:deportecuador.com.ec}") String domain;
 public GoogleIdentity(JdbcTemplate db,AdminAccounts accounts){this.db=db;this.accounts=accounts;}
 public boolean enabled(){return enabled;}
 public boolean linked(UUID id){return db.queryForObject("SELECT count(*) FROM admin_oidc_identity WHERE user_id=?",Integer.class,id)>0;}
 public String subject(OidcUser user){if(!enabled||!"https://accounts.google.com".equals(user.getIssuer().toString())||!Boolean.TRUE.equals(user.getEmailVerified())||domain.isBlank()||!domain.equalsIgnoreCase(user.getClaimAsString("hd"))||user.getSubject()==null||user.getSubject().isBlank()||user.getSubject().length()>255)throw AccountRecovery.invalid();return user.getSubject();}
 public UUID find(OidcUser user){String sub=subject(user);var ids=db.queryForList("SELECT user_id FROM admin_oidc_identity WHERE issuer=? AND subject=?",UUID.class,"https://accounts.google.com",sub);if(ids.size()!=1||accounts.identity(ids.get(0)).isEmpty())throw AccountRecovery.invalid();return ids.get(0);}
 @Transactional public UUID link(UUID id,long version,OidcUser user){String sub=subject(user);db.queryForList("SELECT id FROM admin_user WHERE id=? FOR UPDATE",UUID.class,id);if(accounts.identity(id).isEmpty()||accounts.authVersion(id)!=version||linked(id))throw AccountRecovery.invalid();db.update("INSERT INTO admin_oidc_identity(user_id,issuer,subject) VALUES(?,?,?)",id,"https://accounts.google.com",sub);accounts.audit(id,"GOOGLE_LINKED",id);return id;}
 @Transactional public void unlink(AdminIdentity actor){db.queryForList("SELECT id FROM admin_user WHERE id=? FOR UPDATE",UUID.class,actor.id());db.update("DELETE FROM admin_oidc_identity WHERE user_id=?",actor.id());db.update("UPDATE admin_user SET auth_version=auth_version+1 WHERE id=?",actor.id());accounts.audit(actor.id(),"GOOGLE_UNLINKED",actor.id());}
}
