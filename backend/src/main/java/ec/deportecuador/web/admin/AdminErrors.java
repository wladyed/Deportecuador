package ec.deportecuador.web.admin;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice(basePackages="ec.deportecuador.web.admin")
public class AdminErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String,String>> response(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", error.getReason()==null ? "Operación no permitida." : error.getReason()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String,String>> validation() {
        return ResponseEntity.badRequest().body(Map.of("message", "Revisa los campos obligatorios y el formato de los datos."));
    }
}
