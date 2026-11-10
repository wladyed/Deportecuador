package ec.deportecuador.web.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.*;
import java.util.*;

@RestController
public class EditorialController {
    private final EditorialService cms;
    public EditorialController(EditorialService cms){this.cms=cms;}
    public record Draft(@Min(1) long version,@NotNull Map<String,String> body) {}
    public record Action(@Min(1) long version,@NotBlank String action,Integer revision) {}
    @GetMapping("/api/admin/content") public List<EditorialService.Document> list(@AuthenticationPrincipal AdminIdentity actor,@RequestParam String locale){return cms.list(actor,locale);}
    @GetMapping("/api/admin/content/{locale}/{key}") public EditorialService.Detail detail(@AuthenticationPrincipal AdminIdentity actor,@PathVariable String locale,@PathVariable String key,@RequestParam(defaultValue="0") int page){return cms.detail(actor,key,locale,page);}
    @GetMapping("/api/admin/content/{locale}/{key}/revisions/{revision}") public Map<String,String> revision(@AuthenticationPrincipal AdminIdentity actor,@PathVariable String locale,@PathVariable String key,@PathVariable int revision){return cms.revision(actor,key,locale,revision);}
    @PutMapping("/api/admin/content/{locale}/{key}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(@AuthenticationPrincipal AdminIdentity actor,@PathVariable String locale,@PathVariable String key,@Valid @RequestBody Draft input){cms.save(actor,key,locale,input.version(),input.body());}
    @PostMapping("/api/admin/content/{locale}/{key}/actions") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void action(@AuthenticationPrincipal AdminIdentity actor,@PathVariable String locale,@PathVariable String key,@Valid @RequestBody Action input){cms.action(actor,key,locale,input.version(),input.action(),input.revision());}
    @GetMapping("/api/v1/content/{locale}") public ResponseEntity<Map<String,Map<String,String>>> published(@PathVariable String locale){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cms.published(locale));}
}
