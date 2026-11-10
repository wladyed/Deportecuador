package ec.deportecuador.web;

import com.fasterxml.jackson.databind.*;
import ec.deportecuador.web.admin.AdminSecrets;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;

@RestController
public class WhatsAppGateway {
 private final WhatsAppConversation conversations;private final JdbcTemplate db;private final AdminSecrets secrets;private final ObjectMapper json;
 @Value("${app.whatsapp.enabled:false}") boolean enabled;
 @Value("${app.whatsapp.verify-token:}") String verifyToken;
 @Value("${app.whatsapp.app-secret:}") String appSecret;
 @Value("${app.whatsapp.access-token:}") String accessToken;
 @Value("${app.whatsapp.phone-id:}") String phoneId;
 @Value("${app.whatsapp.api-version:}") String apiVersion;
 @Value("${app.whatsapp.public-url:}") String publicUrl;
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
 WhatsAppGateway(WhatsAppConversation conversations,JdbcTemplate db,AdminSecrets secrets,ObjectMapper json){this.conversations=conversations;this.db=db;this.secrets=secrets;this.json=json;}
 boolean configured(){return enabled&&!verifyToken.isBlank()&&!appSecret.isBlank()&&!accessToken.isBlank()&&phoneId.matches("[0-9]+")&&apiVersion.matches("v[0-9]+\\.0")&&publicUrl.matches("https://[a-zA-Z0-9.-]+(?::[0-9]+)?");}
 @GetMapping(value="/api/whatsapp/webhook",produces="text/plain") public String verify(@RequestParam("hub.mode")String mode,@RequestParam("hub.verify_token")String token,@RequestParam("hub.challenge")String challenge){
  if(!configured())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  if(!mode.equals("subscribe")||!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),verifyToken.getBytes(StandardCharsets.UTF_8)))throw new ResponseStatusException(HttpStatus.FORBIDDEN);return challenge;
 }
 @PostMapping("/api/whatsapp/webhook") public void receive(@RequestHeader(value="X-Hub-Signature-256",defaultValue="")String signature,@RequestBody byte[] body)throws Exception{
  if(!configured())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  if(body.length>32768)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
  if(!validSignature(appSecret,body,signature))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  JsonNode root=json.readTree(body);if(!root.path("object").asText().equals("whatsapp_business_account"))return;
  for(JsonNode entry:root.path("entry"))for(JsonNode change:entry.path("changes")){
   JsonNode value=change.path("value");if(!phoneId.equals(value.path("metadata").path("phone_number_id").asText()))continue;
   for(JsonNode message:value.path("messages")){
    String id=message.path("id").asText(),sender=message.path("from").asText(),text=message.path("text").path("body").asText();
    if(id.isBlank()||id.length()>300||!sender.matches("[1-9][0-9]{7,14}"))continue;
    long timestamp=message.path("timestamp").asLong(0);if(timestamp<=0||timestamp>Instant.now().plusSeconds(300).getEpochSecond())continue;
    if(!message.path("type").asText().equals("text"))text="Hola";
    if(text.length()>500)text="Consulta demasiado larga";
    conversations.receive(id,sender,text,Instant.ofEpochSecond(timestamp),publicUrl);
   }
  }
 }
 static boolean validSignature(String secret,byte[] body,String signature){
  try{if(!signature.matches("sha256=[a-fA-F0-9]{64}"))return false;Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return MessageDigest.isEqual(mac.doFinal(body),HexFormat.of().parseHex(signature.substring(7)));}catch(Exception e){return false;}
 }
 @Scheduled(fixedDelay=5000) public void deliver(){
  if(!configured())return;
  for(var row:db.queryForList("SELECT * FROM whatsapp_reply WHERE sent=FALSE AND expires_at>CURRENT_TIMESTAMP AND next_attempt_at<=CURRENT_TIMESTAMP ORDER BY created_at,message_id LIMIT 20")){
   try{
    var payload=conversations.decode(secrets.decrypt((String)row.get("payload")));String text=payload.get("text");if(text.length()>4000)text=text.substring(0,4000);
    String body=json.writeValueAsString(Map.of("messaging_product","whatsapp","to",payload.get("to"),"type","text","text",Map.of("body",text)));
    var request=HttpRequest.newBuilder(URI.create("https://graph.facebook.com/"+apiVersion+"/"+phoneId+"/messages")).timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+accessToken).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
    var response=client.send(request,HttpResponse.BodyHandlers.discarding());if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalStateException("WhatsApp delivery failed");
    db.update("UPDATE whatsapp_reply SET sent=TRUE,payload='' WHERE message_id=?",row.get("message_id"));
   }catch(Exception e){if(e instanceof InterruptedException){Thread.currentThread().interrupt();return;}int attempt=((Number)row.get("attempts")).intValue()+1;db.update("UPDATE whatsapp_reply SET attempts=?,next_attempt_at=? WHERE message_id=?",attempt,java.sql.Timestamp.from(Instant.now().plusSeconds(Math.min(3600,60L*attempt))),row.get("message_id"));}
  }
 }
 @Scheduled(fixedDelay=60000) public void cleanup(){
  db.update("DELETE FROM whatsapp_session WHERE expires_at<CURRENT_TIMESTAMP");
  db.update("UPDATE whatsapp_reply SET payload='' WHERE expires_at<CURRENT_TIMESTAMP AND payload<>''");
  db.update("DELETE FROM whatsapp_reply WHERE created_at<?",java.sql.Timestamp.from(Instant.now().minusSeconds(86400L*30)));
 }
}
