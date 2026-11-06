package dev.fuken404.opspilot.sla;

import dev.fuken404.opspilot.identity.AppUser;
import dev.fuken404.opspilot.incidents.Tickets;
import dev.fuken404.opspilot.organizations.OrganizationAccess;
import dev.fuken404.opspilot.organizations.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cómo va cada compromiso, para mostrarlo en pantalla. */
@Service
public class SlaStatus {

    private final SlaClockRepository clocks;
    private final SlaCalendars calendars;
    private final Tickets tickets;
    private final OrganizationAccess access;
    private final Clock clock;

    SlaStatus(SlaClockRepository clocks, SlaCalendars calendars, Tickets tickets, OrganizationAccess access, Clock clock) {
        this.clocks = clocks;
        this.calendars = calendars;
        this.tickets = tickets;
        this.access = access;
        this.clock = clock;
    }

    public enum State { RUNNING, AT_RISK, BREACHED, PAUSED, MET, STOPPED }

    /**
     * {@code remainingMinutes} cuenta en el mismo reloj del compromiso (laboral o continuo) y es
     * negativo cuando ya venció. {@code late} dice si se incumplió, aunque ya esté cerrado.
     */
    public record View(UUID ticketId, SlaKind kind, State state, int targetMinutes, boolean businessHours,
                       Instant startedAt, Instant dueAt, Instant metAt, Instant pausedSince, Instant breachedAt,
                       Long remainingMinutes, boolean late) {
    }

    @Transactional(readOnly = true)
    public List<View> forTicket(UUID organizationId, AppUser actor, UUID ticketId) {
        tickets.requireVisible(organizationId, actor, ticketId);
        Instant now = clock.instant();
        return clocks.findByTicketId(ticketId).stream()
            .sorted(Comparator.comparing(SlaClock::getKind))
            .map(c -> view(c, now))
            .toList();
    }

    /** Relojes sin terminar de la organización, para la lista de tickets del equipo. */
    @Transactional(readOnly = true)
    public List<View> open(UUID organizationId, AppUser actor) {
        access.require(organizationId, actor, Role.ADMIN, Role.TECHNICIAN);
        Instant now = clock.instant();
        return clocks.findByOrganizationIdAndMetAtIsNullAndStoppedAtIsNull(organizationId).stream()
            .map(c -> view(c, now))
            .toList();
    }

    private View view(SlaClock c, Instant now) {
        WorkingTime time = calendars.of(c.isBusinessHours());
        boolean late = c.getBreachedAt() != null || c.getMetAt() != null && c.getMetAt().isAfter(c.getDueAt());
        State state;
        Long remaining = null;
        if (c.getStoppedAt() != null) {
            state = c.getMetAt() != null ? State.MET : State.STOPPED;
        } else if (c.getMetAt() != null) {
            state = State.MET;
        } else if (c.getPausedSince() != null) {
            state = State.PAUSED;
            remaining = time.minutesBetween(c.getPausedSince(), c.getDueAt());
        } else {
            remaining = time.minutesBetween(now, c.getDueAt());
            late = late || !now.isBefore(c.getDueAt());
            state = !now.isBefore(c.getDueAt()) ? State.BREACHED
                : !now.isBefore(c.getWarnAt()) ? State.AT_RISK : State.RUNNING;
        }
        return new View(c.getTicketId(), c.getKind(), state, c.getTargetMinutes(), c.isBusinessHours(),
            c.getStartedAt(), c.getPausedSince() != null && c.getMetAt() == null ? null : c.getDueAt(), c.getMetAt(),
            c.getPausedSince(), c.getBreachedAt(), remaining, late);
    }
}
