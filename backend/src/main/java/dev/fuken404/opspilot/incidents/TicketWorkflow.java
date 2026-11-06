package dev.fuken404.opspilot.incidents;

import static dev.fuken404.opspilot.incidents.TicketStatus.CANCELLED;
import static dev.fuken404.opspilot.incidents.TicketStatus.CLOSED;
import static dev.fuken404.opspilot.incidents.TicketStatus.IN_PROGRESS;
import static dev.fuken404.opspilot.incidents.TicketStatus.NEW;
import static dev.fuken404.opspilot.incidents.TicketStatus.RESOLVED;
import static dev.fuken404.opspilot.incidents.TicketStatus.WAITING_CUSTOMER;

import dev.fuken404.opspilot.organizations.Role;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Qué cambios de estado existen y quién puede hacer cada uno. */
final class TicketWorkflow {

    private static final Map<TicketStatus, Set<TicketStatus>> NEXT = Map.of(
        NEW, EnumSet.of(IN_PROGRESS, CANCELLED),
        IN_PROGRESS, EnumSet.of(WAITING_CUSTOMER, RESOLVED, CANCELLED),
        WAITING_CUSTOMER, EnumSet.of(IN_PROGRESS, RESOLVED, CANCELLED),
        RESOLVED, EnumSet.of(IN_PROGRESS, CLOSED),
        CLOSED, EnumSet.noneOf(TicketStatus.class),
        CANCELLED, EnumSet.noneOf(TicketStatus.class));

    private TicketWorkflow() {
    }

    static boolean exists(TicketStatus from, TicketStatus to) {
        return NEXT.get(from).contains(to);
    }

    /**
     * El equipo mueve el ticket por todo el flujo. Quien lo reportó solo puede cancelarlo antes de que
     * alguien lo tome, y confirmar o rechazar la solución (cerrarlo o reabrirlo).
     */
    static Set<TicketStatus> allowed(TicketStatus from, Role role, boolean requester) {
        Set<TicketStatus> next = EnumSet.noneOf(TicketStatus.class);
        next.addAll(NEXT.get(from));
        if (role == Role.CUSTOMER) {
            next.removeIf(to -> !(requester && (from == NEW && to == CANCELLED || from == RESOLVED)));
        }
        return next;
    }
}
