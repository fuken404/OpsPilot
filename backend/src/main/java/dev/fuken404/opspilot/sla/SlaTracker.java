package dev.fuken404.opspilot.sla;

import dev.fuken404.opspilot.incidents.TicketActivity;
import dev.fuken404.opspilot.incidents.TicketStatus;
import dev.fuken404.opspilot.organizations.OrganizationAccess;
import dev.fuken404.opspilot.organizations.Role;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Mueve los relojes según lo que pasa en cada ticket, en la misma transacción del cambio. */
@Component
class SlaTracker {

    private final SlaClockRepository clocks;
    private final SlaPolicies policies;
    private final SlaCalendars calendars;
    private final OrganizationAccess access;

    SlaTracker(SlaClockRepository clocks, SlaPolicies policies, SlaCalendars calendars, OrganizationAccess access) {
        this.clocks = clocks;
        this.policies = policies;
        this.calendars = calendars;
        this.access = access;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    void on(TicketActivity activity) {
        boolean staff = access.roleOf(activity.organizationId(), activity.actorId()).filter(r -> r != Role.CUSTOMER)
            .isPresent();
        Instant at = activity.occurredAt();
        if (activity.action() == TicketActivity.Action.CREATED) {
            start(activity, staff);
            return;
        }
        Map<SlaKind, SlaClock> byKind = clocks.findByTicketId(activity.ticketId()).stream()
            .collect(Collectors.toMap(SlaClock::getKind, Function.identity()));
        SlaClock response = byKind.get(SlaKind.RESPONSE);
        SlaClock resolution = byKind.get(SlaKind.RESOLUTION);
        if (response == null || resolution == null) {
            return; // ticket creado antes de que existiera el SLA
        }
        switch (activity.action()) {
            case COMMENTED -> {
                if (staff && !Boolean.TRUE.equals(activity.details().get("internal"))) {
                    response.meet(at);
                }
            }
            case STATUS_CHANGED -> {
                if (staff) {
                    response.meet(at);
                }
                statusChanged((TicketStatus) activity.details().get("from"), activity.status(), resolution, response, at);
            }
            case TRIAGED -> {
                if (activity.details().containsKey("priority")) {
                    SlaPolicies.Policy policy = policies.forPriority(activity.organizationId(), activity.priority());
                    response.retarget(policy.responseMinutes(), calendars.of(response.isBusinessHours()));
                    resolution.retarget(policy.resolutionMinutes(), calendars.of(resolution.isBusinessHours()));
                }
            }
            case ASSIGNED, CREATED -> {
            }
        }
    }

    private void start(TicketActivity activity, boolean staff) {
        SlaPolicies.Policy policy = policies.forPriority(activity.organizationId(), activity.priority());
        WorkingTime time = calendars.of(policy.businessHours());
        SlaClock response = new SlaClock(activity.organizationId(), activity.ticketId(), SlaKind.RESPONSE,
            policy.responseMinutes(), policy.businessHours(), activity.occurredAt(), time);
        if (staff) {
            response.meet(activity.occurredAt()); // lo abrió el propio equipo: ya está atendido
        }
        clocks.save(response);
        clocks.save(new SlaClock(activity.organizationId(), activity.ticketId(), SlaKind.RESOLUTION,
            policy.resolutionMinutes(), policy.businessHours(), activity.occurredAt(), time));
    }

    private void statusChanged(TicketStatus from, TicketStatus to, SlaClock resolution, SlaClock response, Instant at) {
        WorkingTime time = calendars.of(resolution.isBusinessHours());
        switch (to) {
            case WAITING_CUSTOMER -> resolution.pause(at);
            case IN_PROGRESS -> {
                if (from == TicketStatus.RESOLVED) {
                    resolution.reopen(at, time);
                } else {
                    resolution.resume(at, time);
                }
            }
            case RESOLVED -> {
                // Queda pausado además de cumplido: si el cliente lo reabre, el tiempo resuelto no cuenta.
                resolution.resume(at, time);
                resolution.pause(at);
                resolution.meet(at);
            }
            case CLOSED, CANCELLED -> {
                response.stop(at);
                resolution.stop(at);
            }
            case NEW -> {
            }
        }
    }
}
