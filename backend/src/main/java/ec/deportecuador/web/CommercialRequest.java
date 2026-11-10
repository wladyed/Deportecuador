package ec.deportecuador.web;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="commercial_request") class CommercialRequest {
 @Id UUID id; @Column(name="request_key") UUID requestKey;
 @Column(name="request_type",length=12) String requestType;
 @Column(length=180) String organization; @Column(length=100) String country;
 @Column(name="contact_name",length=180) String contactName;
 @Column(length=254) String email; @Column(length=40) String phone;
 @Column(length=4000) String description; @Column(length=800) String interests;
 @Column(length=2) String locale; @Column(name="policy_version",length=80) String policyVersion;
 @Column(name="consent_at") Instant consentAt; @Column(name="created_at") Instant createdAt;
 @Column(name="assigned_to") UUID assignedTo;
 @Column(length=30) String status; @Column(name="unassigned_alerted") boolean unassignedAlerted;
}
