package ec.deportecuador.web.admin;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/admin/auth")
public class RecoveryController {
 private final AccountRecovery recovery; private final AdminAccounts accounts; private final GoogleIdentity google;
 public RecoveryController(AccountRecovery recovery,AdminAccounts accounts,GoogleIdentity google){this.recovery=recovery;this.accounts=accounts;this.google=google;}
 public record Email(@NotBlank @jakarta.validation.constraints.Email @Size(max=254) String email){}
 public record Reset(@NotBlank @Size(max=128) String token,@NotBlank @Size(min=12,max=72) String password){}
 public record Proof(@NotBlank @Size(max=72) String password,@Size(max=32) String code){}
 @PostMapping("/password/request") public Map<String,String> request(@Valid @RequestBody Email input){recovery.requestPassword(input.email());return Map.of("message","Si la cuenta está habilitada, recibirás un enlace de recuperación.");}
 @PostMapping("/password/reset") public Map<String,Boolean> reset(@Valid @RequestBody Reset input,HttpServletRequest request){recovery.resetPassword(input.token(),input.password());var session=request.getSession(false);if(session!=null)session.invalidate();return Map.of("changed",true);}
 @GetMapping("/security") public Map<String,Object> status(@AuthenticationPrincipal AdminIdentity actor){return Map.of("recoveryCodes",recovery.remaining(actor.id()),"googleEnabled",google.enabled(),"googleLinked",google.linked(actor.id()));}
 private void verify(AdminIdentity actor,Proof proof,HttpServletRequest request,boolean allowRecent){var session=request.getSession(false);boolean recent=allowRecent&&session!=null&&session.getAttribute("admin.recovered") instanceof Instant time&&time.plusSeconds(300).isAfter(Instant.now());if(!recovery.verify(actor,proof.password(),proof.code(),recent))throw AccountRecovery.invalid();}
 @PostMapping("/security/codes") public Map<String,Object> codes(@AuthenticationPrincipal AdminIdentity actor,@Valid @RequestBody Proof proof,HttpServletRequest request){verify(actor,proof,request,false);return Map.of("codes",recovery.codes(actor));}
 @PostMapping("/security/mfa/start") public Map<String,String> start(@AuthenticationPrincipal AdminIdentity actor,@Valid @RequestBody Proof proof,HttpServletRequest request){verify(actor,proof,request,true);var session=request.getSession();String secret=Totp.newSecret();session.setAttribute("admin.newMfa",secret);session.setAttribute("admin.newMfaVersion",accounts.authVersion(actor.id()));session.setAttribute("admin.newMfaExpires",Instant.now().plusSeconds(300));session.setAttribute("admin.newMfaAttempts",0);return Map.of("secret",secret);}
 @PostMapping("/security/mfa/finish") public Map<String,Boolean> finish(@AuthenticationPrincipal AdminIdentity actor,@Valid @RequestBody AdminAuthController.Code input,HttpServletRequest request){var session=request.getSession();if(!(session.getAttribute("admin.newMfaExpires") instanceof Instant expires)||!expires.isAfter(Instant.now()))throw AccountRecovery.invalid();int attempts=(Integer)session.getAttribute("admin.newMfaAttempts")+1;session.setAttribute("admin.newMfaAttempts",attempts);if(attempts>5){session.removeAttribute("admin.newMfaExpires");throw AccountRecovery.invalid();}if(!recovery.replaceMfa(actor,(Long)session.getAttribute("admin.newMfaVersion"),(String)session.getAttribute("admin.newMfa"),input.code()))throw AccountRecovery.invalid();session.invalidate();return Map.of("changed",true);}
 @PostMapping("/security/google/link") public Map<String,String> link(@AuthenticationPrincipal AdminIdentity actor,@Valid @RequestBody Proof proof,HttpServletRequest request){if(!google.enabled())throw AccountRecovery.invalid();verify(actor,proof,request,false);var session=request.getSession();session.setAttribute("admin.googleLink",actor.id());session.setAttribute("admin.googleLinkVersion",accounts.authVersion(actor.id()));session.setAttribute("admin.googleLinkExpires",Instant.now().plusSeconds(300));return Map.of("url","/oauth2/authorization/google");}
 @PostMapping("/security/google/unlink") public Map<String,Boolean> unlink(@AuthenticationPrincipal AdminIdentity actor,@Valid @RequestBody Proof proof,HttpServletRequest request){verify(actor,proof,request,false);google.unlink(actor);request.getSession().invalidate();return Map.of("changed",true);}
}
