package ec.deportecuador.web;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
interface RequestRepository extends JpaRepository<CommercialRequest,UUID> {Optional<CommercialRequest> findByRequestKey(UUID key);}
