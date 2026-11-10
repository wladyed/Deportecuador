package ec.deportecuador.web;
import jakarta.validation.constraints.*;
import java.util.List;
record Intake(
 @NotBlank @Pattern(regexp="DEMO|ADVICE") String type,
 @NotBlank @Size(max=180) String organization,
 @NotBlank @Size(max=100) String country,
 @NotBlank @Size(max=180) String name,
 @NotBlank @Email @Size(max=254) String email,
 @Size(max=40) String phone,
 @Size(max=4000) String description,
 @NotEmpty @Size(max=6) List<@NotBlank String> interests,
 @NotBlank @Pattern(regexp="es|en|pt") String locale,
 @AssertTrue boolean consent,
 @NotBlank @Pattern(regexp="draft-2026-10-19") String policyVersion,
 @Size(max=0) String website) {}
