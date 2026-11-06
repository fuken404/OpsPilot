package dev.fuken404.opspilot.incidents;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Algo cambió en un ticket. Se publica dentro de la transacción del cambio, con el estado y la
 * prioridad que el ticket quedó teniendo.
 */
public record TicketActivity(UUID organizationId, UUID ticketId, UUID actorId, Action action, Map<String, Object> details,
                             TicketStatus status, TicketPriority priority, UUID requesterId, Instant occurredAt) {

    /** Una nota interna no debe llegar ni como aviso a quien reportó el ticket. */
    public boolean isInternal() {
        return Boolean.TRUE.equals(details.get("internal"));
    }

    public enum Action {
        CREATED, STATUS_CHANGED, ASSIGNED, TRIAGED, COMMENTED
    }
}
