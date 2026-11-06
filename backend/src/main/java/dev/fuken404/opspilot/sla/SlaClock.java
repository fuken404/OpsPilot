package dev.fuken404.opspilot.sla;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Un compromiso de tiempo sobre un ticket. El vencimiento siempre se calcula igual: inicio + objetivo
 * + minutos pausados, contados con el mismo reloj (laboral o continuo). Así, pausar, reanudar o
 * cambiar la prioridad solo recalcula; nunca acumula errores.
 */
@Entity
@Table(name = "sla_clock")
class SlaClock {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "ticket_id", nullable = false, updatable = false)
    private UUID ticketId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private SlaKind kind;

    @Column(name = "target_minutes", nullable = false)
    private int targetMinutes;

    /** Se fija al crear el reloj: los minutos pausados están medidos con este mismo reloj. */
    @Column(name = "business_hours", nullable = false, updatable = false)
    private boolean businessHours;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "paused_since")
    private Instant pausedSince;

    @Column(name = "paused_minutes", nullable = false)
    private long pausedMinutes;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "warn_at", nullable = false)
    private Instant warnAt;

    @Column(name = "met_at")
    private Instant metAt;

    @Column(name = "stopped_at")
    private Instant stoppedAt;

    @Column(name = "warned_at")
    private Instant warnedAt;

    @Column(name = "breached_at")
    private Instant breachedAt;

    @Version
    private long version;

    protected SlaClock() {
    }

    SlaClock(UUID organizationId, UUID ticketId, SlaKind kind, int targetMinutes, boolean businessHours,
             Instant startedAt, WorkingTime time) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.ticketId = ticketId;
        this.kind = kind;
        this.targetMinutes = targetMinutes;
        this.businessHours = businessHours;
        this.startedAt = startedAt;
        recompute(time);
    }

    private void recompute(WorkingTime time) {
        dueAt = time.add(startedAt, targetMinutes + pausedMinutes);
        warnAt = time.add(startedAt, (long) Math.ceil(targetMinutes * 0.8) + pausedMinutes);
    }

    boolean isFinished() {
        return metAt != null || stoppedAt != null;
    }

    void pause(Instant now) {
        if (!isFinished() && pausedSince == null) {
            pausedSince = now;
        }
    }

    void resume(Instant now, WorkingTime time) {
        if (pausedSince != null) {
            pausedMinutes += Math.max(0, time.minutesBetween(pausedSince, now));
            pausedSince = null;
            recompute(time);
        }
    }

    void meet(Instant now) {
        if (!isFinished()) {
            metAt = now;
        }
    }

    /** El cliente rechazó la solución: el reloj vuelve a correr desde donde quedó. */
    void reopen(Instant now, WorkingTime time) {
        if (stoppedAt == null) {
            metAt = null;
            resume(now, time);
        }
    }

    void stop(Instant now) {
        if (stoppedAt == null) {
            stoppedAt = now;
        }
    }

    void retarget(int targetMinutes, WorkingTime time) {
        this.targetMinutes = targetMinutes;
        recompute(time);
    }

    void warn(Instant now) {
        if (warnedAt == null) {
            warnedAt = now;
        }
    }

    void breach(Instant now) {
        warn(now);
        if (breachedAt == null) {
            breachedAt = now;
        }
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    UUID getTicketId() {
        return ticketId;
    }

    SlaKind getKind() {
        return kind;
    }

    int getTargetMinutes() {
        return targetMinutes;
    }

    boolean isBusinessHours() {
        return businessHours;
    }

    Instant getStartedAt() {
        return startedAt;
    }

    Instant getPausedSince() {
        return pausedSince;
    }

    Instant getDueAt() {
        return dueAt;
    }

    Instant getWarnAt() {
        return warnAt;
    }

    Instant getMetAt() {
        return metAt;
    }

    Instant getStoppedAt() {
        return stoppedAt;
    }

    Instant getWarnedAt() {
        return warnedAt;
    }

    Instant getBreachedAt() {
        return breachedAt;
    }
}
