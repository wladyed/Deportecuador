package ec.deportecuador.web;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import jakarta.validation.Valid;
import java.util.*;
@RestController class RequestController {
 private final RequestService service;RequestController(RequestService service){this.service=service;}
 @PostMapping("/api/v1/requests") ResponseEntity<Map<String,Object>> receive(@RequestHeader("Idempotency-Key")UUID key,@Valid @RequestBody Intake in){return ResponseEntity.status(201).body(Map.of("id",service.receive(key,in),"status","RECEIVED"));}
}
