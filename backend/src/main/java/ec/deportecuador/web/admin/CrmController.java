package ec.deportecuador.web.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.*;

@RestController @RequestMapping("/api/admin")
public class CrmController {
    private final CrmService crm;
    public CrmController(CrmService crm){this.crm=crm;}
    public record Update(@Min(0) long version,@NotBlank @Pattern(regexp="NEW|CONTACTED|PROPOSAL|WON|LOST") String status,Instant nextContactAt,@Size(max=1000) String closeReason) {}
    public record Note(@NotBlank String kind,@NotBlank @Size(max=4000) String body) {}
    @GetMapping("/requests/{id}/crm") public CrmService.Detail detail(@AuthenticationPrincipal AdminIdentity actor,@PathVariable UUID id,@RequestParam(defaultValue="0") int page){return crm.detail(actor,id,page);}
    @PatchMapping("/requests/{id}/workflow") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@AuthenticationPrincipal AdminIdentity actor,@PathVariable UUID id,@Valid @RequestBody Update input){crm.update(actor,id,input.version(),input.status(),input.nextContactAt(),input.closeReason());}
    @PostMapping("/requests/{id}/activities") @ResponseStatus(HttpStatus.CREATED)
    public void note(@AuthenticationPrincipal AdminIdentity actor,@PathVariable UUID id,@Valid @RequestBody Note input){crm.note(actor,id,input.kind(),input.body());}
    @GetMapping("/notifications") public List<CrmService.Notice> notices(@AuthenticationPrincipal AdminIdentity actor){return crm.notices(actor);}
    @PatchMapping("/notifications/{id}/read") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@AuthenticationPrincipal AdminIdentity actor,@PathVariable UUID id){crm.read(actor,id);}
}
