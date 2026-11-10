package ec.deportecuador.web.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
public class MediaController {
 private final MediaService media;
 public MediaController(MediaService media){this.media=media;}
 public record Edit(@Min(1)long version,@NotBlank @Size(max=180)String title,boolean archived){}
 @GetMapping("/api/admin/media") public MediaService.Page list(@AuthenticationPrincipal AdminIdentity actor,@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="false")boolean archived,@RequestParam(defaultValue="0")int page){return media.list(actor,q,archived,page);}
 @PostMapping(value="/api/admin/media",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED) public MediaService.Asset upload(@AuthenticationPrincipal AdminIdentity actor,@RequestParam MultipartFile file,@RequestParam String title)throws IOException{return media.upload(actor,file,title);}
 @PatchMapping("/api/admin/media/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void update(@AuthenticationPrincipal AdminIdentity actor,@PathVariable UUID id,@Valid @RequestBody Edit input){media.update(actor,id,input.version(),input.title(),input.archived());}
 @GetMapping("/api/admin/media/{id}/file") public ResponseEntity<byte[]> preview(@AuthenticationPrincipal AdminIdentity actor,@PathVariable UUID id)throws IOException{EditorialService.requireEditor(actor);return file(id);}
 @GetMapping("/api/v1/media/{id}") public ResponseEntity<byte[]> published(@PathVariable UUID id)throws IOException{if(!media.isPublic(id))throw new ResponseStatusException(HttpStatus.NOT_FOUND);return file(id);}
 private ResponseEntity<byte[]> file(UUID id)throws IOException{var asset=media.get(id);var disposition=asset.contentType().equals("application/pdf")?ContentDisposition.attachment():ContentDisposition.inline();return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","default-src 'none'; sandbox").header(HttpHeaders.CONTENT_DISPOSITION,disposition.filename(asset.filename(),StandardCharsets.UTF_8).build().toString()).contentType(MediaType.parseMediaType(asset.contentType())).body(media.read(id));}
}
