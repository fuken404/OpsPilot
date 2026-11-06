package dev.fuken404.opspilot.sla;

import java.time.Instant;
import java.util.UUID;

/**
 * Un reloj entró en riesgo (80 % del tiempo consumido) o venció. Vencer escala el ticket a los
 * administradores de la organización.
 */
public record SlaAlert(UUID organizationId, UUID ticketId, SlaKind kind, Level level, Instant dueAt, Instant occurredAt) {

    public enum Level {
        WARNING, BREACHED
    }
}
