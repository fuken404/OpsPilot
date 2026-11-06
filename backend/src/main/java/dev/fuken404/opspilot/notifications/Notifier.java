package dev.fuken404.opspilot.notifications;

import dev.fuken404.opspilot.identity.AppUser;
import dev.fuken404.opspilot.identity.Users;
import dev.fuken404.opspilot.incidents.TicketActivity;
import dev.fuken404.opspilot.incidents.TicketStatus;
import dev.fuken404.opspilot.incidents.Tickets;
import dev.fuken404.opspilot.organizations.OrganizationAccess;
import dev.fuken404.opspilot.organizations.Organizations;
import dev.fuken404.opspilot.organizations.Role;
import dev.fuken404.opspilot.sla.SlaAlert;
import dev.fuken404.opspilot.sla.SlaKind;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decide quién recibe un correo por cada cambio y lo deja en la bandeja de salida, en la misma
 * transacción. Nunca le escribe a quien hizo el cambio. Los correos no copian el contenido de los
 * comentarios: invitan a abrir el ticket, donde se aplican los permisos.
 */
@Component
class Notifier {

    private static final Map<TicketStatus, String> STATUS_TEXT = Map.of(
        TicketStatus.WAITING_CUSTOMER, "El equipo necesita tu respuesta para continuar.",
        TicketStatus.RESOLVED, "El equipo lo marcó como resuelto. Ciérralo si quedó bien o reábrelo si no.",
        TicketStatus.CLOSED, "El ticket quedó cerrado.",
        TicketStatus.CANCELLED, "El ticket fue cancelado.");

    private final EmailOutboxRepository outbox;
    private final Tickets tickets;
    private final Organizations organizations;
    private final OrganizationAccess access;
    private final Users users;
    private final String appUrl;
    private final Clock clock;

    Notifier(EmailOutboxRepository outbox, Tickets tickets, Organizations organizations, OrganizationAccess access,
             Users users, @Value("${opspilot.app-url}") String appUrl, Clock clock) {
        this.outbox = outbox;
        this.tickets = tickets;
        this.organizations = organizations;
        this.access = access;
        this.users = users;
        this.appUrl = appUrl;
        this.clock = clock;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    void on(TicketActivity activity) {
        Tickets.Snapshot ticket = tickets.snapshot(activity.organizationId(), activity.ticketId()).orElse(null);
        if (ticket == null) {
            return;
        }
        UUID org = activity.organizationId();
        String actor = users.findById(activity.actorId()).map(AppUser::getDisplayName).orElse("Alguien");
        boolean byStaff = access.roleOf(org, activity.actorId()).filter(r -> r != Role.CUSTOMER).isPresent();
        boolean byRequester = activity.actorId().equals(ticket.requesterId());
        Set<UUID> to = new LinkedHashSet<>();
        String text;
        switch (activity.action()) {
            case CREATED -> {
                if (byStaff) {
                    return;
                }
                to.addAll(staff(org));
                text = actor + " reportó un ticket nuevo.";
            }
            case ASSIGNED -> {
                Object assignee = activity.details().get("to");
                if (assignee == null) {
                    return;
                }
                to.add((UUID) assignee);
                text = actor + " te asignó este ticket.";
            }
            case STATUS_CHANGED -> {
                if ("CUSTOMER_REPLIED".equals(activity.details().get("reason"))) {
                    return; // ya se avisa por el comentario
                }
                if (byRequester) {
                    to.addAll(handlers(org, ticket));
                    text = actor + " cambió el estado a: " + label(activity.status()) + ".";
                } else if (STATUS_TEXT.containsKey(activity.status())) {
                    to.add(ticket.requesterId());
                    text = STATUS_TEXT.get(activity.status());
                } else {
                    return;
                }
            }
            case COMMENTED, ATTACHED -> {
                String what = activity.action() == TicketActivity.Action.COMMENTED ? "comentó" : "adjuntó un archivo";
                if (activity.isInternal()) {
                    if (ticket.assigneeId() != null) {
                        to.add(ticket.assigneeId());
                    }
                    text = actor + " dejó una nota interna (" + what + ").";
                } else if (byRequester) {
                    to.addAll(handlers(org, ticket));
                    text = actor + " " + what + " en el ticket.";
                } else {
                    to.add(ticket.requesterId());
                    text = actor + " " + what + " en tu ticket.";
                }
            }
            default -> {
                return;
            }
        }
        to.remove(activity.actorId());
        queue(org, ticket, to, text);
    }

    /** En riesgo: avisa al responsable. Vencido: escala a los administradores. */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    void on(SlaAlert alert) {
        Tickets.Snapshot ticket = tickets.snapshot(alert.organizationId(), alert.ticketId()).orElse(null);
        if (ticket == null) {
            return;
        }
        String commitment = alert.kind() == SlaKind.RESPONSE ? "primera respuesta" : "resolución";
        Set<UUID> to = new LinkedHashSet<>();
        String text;
        if (alert.level() == SlaAlert.Level.BREACHED) {
            to.addAll(organizations.memberIds(alert.organizationId(), Role.ADMIN));
            if (ticket.assigneeId() != null) {
                to.add(ticket.assigneeId());
            }
            text = "Venció el SLA de " + commitment + ". El ticket se escaló a los administradores.";
        } else {
            to.addAll(handlers(alert.organizationId(), ticket));
            text = "El SLA de " + commitment + " ya consumió el 80 % del tiempo.";
        }
        queue(alert.organizationId(), ticket, to, text);
    }

    /** El responsable; si no hay, todo el equipo. */
    private Collection<UUID> handlers(UUID organizationId, Tickets.Snapshot ticket) {
        return ticket.assigneeId() != null ? List.of(ticket.assigneeId()) : staff(organizationId);
    }

    private List<UUID> staff(UUID organizationId) {
        return organizations.memberIds(organizationId, Role.ADMIN, Role.TECHNICIAN);
    }

    private void queue(UUID organizationId, Tickets.Snapshot ticket, Set<UUID> recipients, String text) {
        if (recipients.isEmpty()) {
            return;
        }
        String subject = "[OpsPilot] #" + ticket.number() + " " + ticket.title();
        String body = text + "\n\n#" + ticket.number() + " " + ticket.title() + "\nEstado: " + label(ticket.status())
            + "\n\nÁbrelo aquí: " + appUrl + "/app/tickets/" + ticket.id()
            + "\n\n—\nOpsPilot. Recibes este correo porque participas en este ticket.";
        List<EmailOutboxEntry> entries = new ArrayList<>();
        users.findAllById(recipients).values().stream()
            .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
            .forEach(u -> entries.add(new EmailOutboxEntry(organizationId, ticket.id(), u.getEmail(), subject, body,
                clock.instant())));
        outbox.saveAll(entries);
    }

    private static String label(TicketStatus status) {
        return switch (status) {
            case NEW -> "Nuevo";
            case IN_PROGRESS -> "En curso";
            case WAITING_CUSTOMER -> "Esperando al cliente";
            case RESOLVED -> "Resuelto";
            case CLOSED -> "Cerrado";
            case CANCELLED -> "Cancelado";
        };
    }
}
