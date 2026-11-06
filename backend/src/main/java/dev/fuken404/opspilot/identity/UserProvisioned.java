package dev.fuken404.opspilot.identity;

import java.util.UUID;

/** Primera vez que alguien entra. Otros módulos reaccionan, por ejemplo para aceptar invitaciones. */
public record UserProvisioned(UUID userId, String email, boolean emailVerified) {
}
