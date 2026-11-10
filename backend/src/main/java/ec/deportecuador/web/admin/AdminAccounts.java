package ec.deportecuador.web.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class AdminAccounts {
    private final JdbcTemplate db;
    private final AdminSecrets secrets;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
    private final String dummyHash = passwords.encode(UUID.randomUUID().toString());
    public AdminAccounts(JdbcTemplate db, AdminSecrets secrets) { this.db = db; this.secrets = secrets; }

    public record Pending(UUID userId, String email, String name, String passwordHash, String secret,
                          String invitationHash, boolean bootstrap, Instant expiresAt) implements Serializable {}
    public record UserView(UUID id, String email, String name, AdminIdentity.Role role, boolean enabled, boolean enrolled) {}
    public record Invitation(UserView user, String token, Instant expiresAt) {}

    public boolean setupRequired() { return !Boolean.TRUE.equals(db.queryForObject("SELECT completed FROM admin_setup WHERE id=1", Boolean.class)); }
    public long authVersion(UUID id){var values=db.queryForList("SELECT auth_version FROM admin_user WHERE id=?",Long.class,id);return values.isEmpty()?-1:values.get(0);}

    public Optional<AdminIdentity> identity(UUID id) {
        return db.query("SELECT id,email,display_name,role FROM admin_user WHERE id=? AND enabled=TRUE AND totp_secret IS NOT NULL",
            (rs,n) -> new AdminIdentity(rs.getObject("id",UUID.class), rs.getString("email"), rs.getString("display_name"), AdminIdentity.Role.valueOf(rs.getString("role"))), id).stream().findFirst();
    }

    private Map<String,Object> lockedUser(UUID id) {
        var rows = db.queryForList("SELECT * FROM admin_user WHERE id=? FOR UPDATE", id);
        return rows.isEmpty() ? null : rows.get(0);
    }
    private boolean allowed(Map<String,Object> user) {
        if (user == null || !Boolean.TRUE.equals(user.get("enabled"))) return false;
        Object until = user.get("locked_until");
        return until == null || !asInstant(until).isAfter(Instant.now());
    }
    private static Instant asInstant(Object value) {
        if (value instanceof java.time.OffsetDateTime t) return t.toInstant();
        return ((Timestamp)value).toInstant();
    }
    private void failure(UUID id) {
        db.update("UPDATE admin_user SET failed_attempts=failed_attempts+1 WHERE id=?", id);
        int count = db.queryForObject("SELECT failed_attempts FROM admin_user WHERE id=?", Integer.class, id);
        if (count >= 5) db.update("UPDATE admin_user SET locked_until=?,failed_attempts=0 WHERE id=?", Timestamp.from(Instant.now().plusSeconds(900)), id);
        audit(id, "LOGIN_REJECTED", id);
    }

    @Transactional
    public UUID checkPassword(String email, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) return null;
        var ids = db.queryForList("SELECT id FROM admin_user WHERE email=?", UUID.class, email.trim().toLowerCase(Locale.ROOT));
        if (ids.isEmpty()) { passwords.matches(password, dummyHash); return null; }
        UUID id = ids.get(0); var user = lockedUser(id);
        String hash = (String)user.get("password_hash");
        if (!allowed(user) || hash == null || user.get("totp_secret") == null) { passwords.matches(password, dummyHash); return null; }
        if (!passwords.matches(password, hash)) { failure(id); return null; }
        return id; // No authentication until the second factor succeeds.
    }

    @Transactional
    public AdminIdentity checkMfa(UUID id, String code) {
        var user = lockedUser(id);
        if (!allowed(user) || user.get("totp_secret") == null) return null;
        long step = Totp.match(secrets.decrypt((String)user.get("totp_secret")), code, Instant.now(), ((Number)user.get("last_totp_step")).longValue());
        if (step < 0) { failure(id); return null; }
        db.update("UPDATE admin_user SET last_totp_step=?,failed_attempts=0,locked_until=NULL WHERE id=?", step, id);
        audit(id, "LOGIN", id);
        return identity(id).orElse(null);
    }
    public record PasswordProof(UUID id,long version){}
    @Transactional public PasswordProof checkPasswordProof(String email,String password){UUID id=checkPassword(email,password);return id==null?null:new PasswordProof(id,authVersion(id));}
    @Transactional public AdminIdentity checkFactorVersion(UUID id,String code,long version){lockedUser(id);return version==authVersion(id)?checkFactor(id,code):null;}
    @Transactional public AdminIdentity checkFactor(UUID id,String code){
        if(code==null)return null;
        if(code.matches("[0-9]{6}"))return checkMfa(id,code);
        var user=lockedUser(id);if(!allowed(user)||user.get("totp_secret")==null)return null;
        if(db.update("UPDATE admin_recovery_code SET used_at=CURRENT_TIMESTAMP WHERE code_hash=? AND user_id=? AND used_at IS NULL",AdminSecrets.hash(code.toLowerCase(Locale.ROOT)),id)!=1){failure(id);return null;}
        db.update("UPDATE admin_user SET failed_attempts=0,locked_until=NULL WHERE id=?",id);audit(id,"RECOVERY_CODE_USED",id);return identity(id).orElse(null);
    }

    public Pending beginEnrollment(String token, String email, String name, String password, boolean bootstrap) {
        if (password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña debe tener al menos 12 caracteres y como máximo 72 bytes.");
        UUID id;
        String invitationHash = AdminSecrets.hash(token);
        if (bootstrap) {
            if (!setupRequired() || !secrets.matchesSetup(token)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Activación no disponible.");
            id = UUID.randomUUID();
        } else {
            var users = db.queryForList("SELECT u.id,u.email,u.display_name FROM admin_invitation i JOIN admin_user u ON u.id=i.user_id WHERE i.token_hash=? AND i.used_at IS NULL AND i.expires_at>CURRENT_TIMESTAMP AND u.enabled=TRUE AND u.password_hash IS NULL", invitationHash);
            if (users.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invitación inválida o vencida.");
            var user = users.get(0); id = (UUID)user.get("id"); email = (String)user.get("email"); name = (String)user.get("display_name");
        }
        return new Pending(id, email.trim().toLowerCase(Locale.ROOT), name.trim(), passwords.encode(password), Totp.newSecret(), invitationHash, bootstrap, Instant.now().plusSeconds(300));
    }

    @Transactional
    public AdminIdentity finishEnrollment(Pending pending, String code) {
        if (pending == null || pending.expiresAt().isBefore(Instant.now())) return null;
        long step = Totp.match(pending.secret(), code, Instant.now(), -1);
        if (step < 0) return null;
        // Serializes first-admin setup and account-management operations.
        boolean completed = Boolean.TRUE.equals(db.queryForObject("SELECT completed FROM admin_setup WHERE id=1 FOR UPDATE", Boolean.class));
        if (pending.bootstrap()) {
            if (completed) return null;
            db.update("INSERT INTO admin_user(id,email,display_name,role,password_hash,totp_secret,last_totp_step,created_at) VALUES(?,?,?,'ADMIN',?,?,?,?)",
                pending.userId(), pending.email(), pending.name(), pending.passwordHash(), secrets.encrypt(pending.secret()), step, Timestamp.from(Instant.now()));
            db.update("UPDATE admin_setup SET completed=TRUE WHERE id=1");
        } else {
            int consumed = db.update("UPDATE admin_invitation SET used_at=CURRENT_TIMESTAMP WHERE token_hash=? AND user_id=? AND used_at IS NULL AND expires_at>CURRENT_TIMESTAMP", pending.invitationHash(), pending.userId());
            if (consumed != 1) return null;
            int updated = db.update("UPDATE admin_user SET password_hash=?,totp_secret=?,last_totp_step=? WHERE id=? AND enabled=TRUE AND password_hash IS NULL", pending.passwordHash(), secrets.encrypt(pending.secret()), step, pending.userId());
            if (updated != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "La invitación ya no está disponible.");
        }
        audit(pending.userId(), "ACCOUNT_ACTIVATED", pending.userId());
        return identity(pending.userId()).orElseThrow();
    }

    public List<UserView> users() {
        return db.query("SELECT id,email,display_name,role,enabled,totp_secret FROM admin_user ORDER BY created_at DESC",
            (rs,n) -> new UserView(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("display_name"), AdminIdentity.Role.valueOf(rs.getString("role")), rs.getBoolean("enabled"), rs.getString("totp_secret") != null));
    }

    @Transactional
    public Invitation invite(AdminIdentity actor, String email, String name, AdminIdentity.Role role) {
        requireAdmin(actor);
        db.queryForObject("SELECT completed FROM admin_setup WHERE id=1 FOR UPDATE", Boolean.class);
        email = email.trim().toLowerCase(Locale.ROOT);
        if (db.queryForObject("SELECT count(*) FROM admin_user WHERE email=?", Integer.class, email) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un usuario con ese correo.");
        UUID id = UUID.randomUUID(); String token = AdminSecrets.token(); Instant expires = Instant.now().plusSeconds(86400);
        db.update("INSERT INTO admin_user(id,email,display_name,role,created_at) VALUES(?,?,?,?,?)", id, email, name.trim(), role.name(), Timestamp.from(Instant.now()));
        db.update("INSERT INTO admin_invitation(token_hash,user_id,expires_at) VALUES(?,?,?)", AdminSecrets.hash(token), id, Timestamp.from(expires));
        audit(actor.id(), "USER_INVITED", id);
        return new Invitation(new UserView(id,email,name.trim(),role,true,false), token, expires);
    }

    @Transactional
    public void updateUser(AdminIdentity actor, UUID id, AdminIdentity.Role role, boolean enabled) {
        requireAdmin(actor);
        db.queryForObject("SELECT completed FROM admin_setup WHERE id=1 FOR UPDATE", Boolean.class);
        var user = lockedUser(id);
        if (user == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if ("ADMIN".equals(user.get("role")) && Boolean.TRUE.equals(user.get("enabled")) && (role != AdminIdentity.Role.ADMIN || !enabled)) {
            int admins = db.queryForObject("SELECT count(*) FROM admin_user WHERE role='ADMIN' AND enabled=TRUE AND totp_secret IS NOT NULL AND id<>?", Integer.class, id);
            if (admins == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Debe permanecer al menos un administrador activo.");
        }
        db.update("UPDATE admin_user SET role=?,enabled=? WHERE id=?", role.name(), enabled, id);
        audit(actor.id(), "USER_PERMISSIONS_CHANGED", id);
    }

    @Transactional
    public Invitation renewInvitation(AdminIdentity actor, UUID id) {
        requireAdmin(actor);
        db.queryForObject("SELECT completed FROM admin_setup WHERE id=1 FOR UPDATE", Boolean.class);
        var user = lockedUser(id);
        if (user == null || !Boolean.TRUE.equals(user.get("enabled")) || user.get("password_hash") != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede renovar una cuenta activa pendiente de activación.");
        db.update("UPDATE admin_invitation SET used_at=CURRENT_TIMESTAMP WHERE user_id=? AND used_at IS NULL", id);
        String token=AdminSecrets.token(); Instant expires=Instant.now().plusSeconds(86400);
        db.update("INSERT INTO admin_invitation(token_hash,user_id,expires_at) VALUES(?,?,?)",AdminSecrets.hash(token),id,Timestamp.from(expires));
        audit(actor.id(), "INVITATION_RENEWED", id);
        return new Invitation(new UserView(id,(String)user.get("email"),(String)user.get("display_name"),AdminIdentity.Role.valueOf((String)user.get("role")),true,false),token,expires);
    }

    public void audit(UUID actor, String action, UUID target) {
        db.update("INSERT INTO admin_audit(id,actor_id,action,target_id,created_at) VALUES(?,?,?,?,?)", UUID.randomUUID(), actor, action, target, Timestamp.from(Instant.now()));
    }
    public static void requireAdmin(AdminIdentity actor) {
        if (actor == null || actor.role() != AdminIdentity.Role.ADMIN) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}
