package dev.fuken404.opspilot.incidents;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Algo cambió en un ticket. Se publica dentro de la transacción del cambio, con el estado y la
 * prioridad que el ticket quedó teniendo.
 */
public record TicketActivity(UUID organizationId, UUID ticketId, UUID actorId, Action action, Map<String, Object> details,
                             TicketStatus status, TicketPriority priority, Instant occurredAt) {

    public enum Action {
        CREATED, STATUS_CHANGED, ASSIGNED, TRIAGED, COMMENTED
    }
}
