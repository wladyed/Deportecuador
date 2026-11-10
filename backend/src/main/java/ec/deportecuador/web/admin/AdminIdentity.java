package ec.deportecuador.web.admin;

import java.io.Serializable;
import java.util.UUID;

public record AdminIdentity(UUID id, String email, String name, Role role) implements Serializable {
    public enum Role { ADMIN, EDITOR, COMMERCIAL, VIEWER }
}
