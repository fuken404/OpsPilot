package dev.fuken404.opspilot.incidents;

import java.util.Map;
import java.util.UUID;

/** Algo cambió en un ticket. Se publica dentro de la transacción del cambio. */
public record TicketActivity(UUID organizationId, UUID ticketId, UUID actorId, Action action, Map<String, Object> details) {

    public enum Action {
        CREATED, STATUS_CHANGED, ASSIGNED, TRIAGED, COMMENTED
    }
}
