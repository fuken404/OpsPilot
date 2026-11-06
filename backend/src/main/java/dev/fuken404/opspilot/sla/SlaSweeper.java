package dev.fuken404.opspilot.sla;

import dev.fuken404.opspilot.organizations.Organizations;
import dev.fuken404.opspilot.shared.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Revisa los relojes en curso: avisa los que llevan el 80 % del tiempo y escala los vencidos.
 * Es idempotente: cada aviso queda marcado en el reloj, así que repetir el barrido no duplica nada.
 */
@Service
public class SlaSweeper {

    private static final Logger log = LoggerFactory.getLogger(SlaSweeper.class);

    private final SlaClockRepository clocks;
    private final Organizations organizations;
    private final TenantContext tenant;
    private final TransactionTemplate transaction;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    SlaSweeper(SlaClockRepository clocks, Organizations organizations, TenantContext tenant,
               TransactionTemplate transaction, ApplicationEventPublisher events, Clock clock) {
        this.clocks = clocks;
        this.organizations = organizations;
        this.tenant = tenant;
        this.transaction = transaction;
        this.events = events;
        this.clock = clock;
    }

    public record Result(int warnings, int breaches) {

        Result plus(Result other) {
            return new Result(warnings + other.warnings, breaches + other.breaches);
        }
    }

    /** Una transacción por organización: Row Level Security sigue aplicando y un fallo no frena a las demás. */
    public Result sweep() {
        Instant now = clock.instant();
        Result total = new Result(0, 0);
        for (UUID organizationId : organizations.allIds()) {
            try {
                total = total.plus(transaction.execute(tx -> sweep(organizationId, now)));
            } catch (RuntimeException error) {
                log.error("Falló el barrido de SLA de la organización {}", organizationId, error);
            }
        }
        if (total.warnings() + total.breaches() > 0) {
            log.info("Barrido de SLA: {} en riesgo, {} vencidos", total.warnings(), total.breaches());
        }
        return total;
    }

    private Result sweep(UUID organizationId, Instant now) {
        tenant.enter(organizationId);
        int warnings = 0;
        int breaches = 0;
        for (SlaClock pending : clocks.lockPending(organizationId, now)) {
            if (pending.getBreachedAt() == null && !now.isBefore(pending.getDueAt())) {
                pending.breach(now);
                publish(pending, SlaAlert.Level.BREACHED, now);
                breaches++;
            } else if (pending.getWarnedAt() == null) {
                pending.warn(now);
                publish(pending, SlaAlert.Level.WARNING, now);
                warnings++;
            }
        }
        return new Result(warnings, breaches);
    }

    private void publish(SlaClock pending, SlaAlert.Level level, Instant now) {
        events.publishEvent(new SlaAlert(pending.getOrganizationId(), pending.getTicketId(), pending.getKind(), level,
            pending.getDueAt(), now));
    }
}
