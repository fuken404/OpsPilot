package dev.fuken404.opspilot.sla;

import dev.fuken404.opspilot.identity.AppUser;
import dev.fuken404.opspilot.incidents.TicketPriority;
import dev.fuken404.opspilot.organizations.OrganizationAccess;
import dev.fuken404.opspilot.organizations.Role;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SlaPolicies {

    /** Minutos de respuesta y de resolución. Lo crítico corre todo el tiempo; el resto, en horario laboral. */
    static final Map<TicketPriority, Policy> DEFAULTS = Map.of(
        TicketPriority.CRITICAL, new Policy(TicketPriority.CRITICAL, 15, 4 * 60, false),
        TicketPriority.HIGH, new Policy(TicketPriority.HIGH, 60, 8 * 60, true),
        TicketPriority.MEDIUM, new Policy(TicketPriority.MEDIUM, 4 * 60, 24 * 60, true),
        TicketPriority.LOW, new Policy(TicketPriority.LOW, 8 * 60, 40 * 60, true));

    private final SlaPolicyRepository policies;
    private final OrganizationAccess access;

    SlaPolicies(SlaPolicyRepository policies, OrganizationAccess access) {
        this.policies = policies;
        this.access = access;
    }

    public record Policy(TicketPriority priority, int responseMinutes, int resolutionMinutes, boolean businessHours) {
    }

    /** Dentro de una transacción con la organización activa. */
    Policy forPriority(UUID organizationId, TicketPriority priority) {
        return policies.findByOrganizationIdAndPriority(organizationId, priority)
            .map(SlaPolicies::view)
            .orElse(DEFAULTS.get(priority));
    }

    @Transactional(readOnly = true)
    public List<Policy> list(UUID organizationId, AppUser actor) {
        access.require(organizationId, actor, Role.ADMIN, Role.TECHNICIAN);
        return Arrays.stream(TicketPriority.values()).map(p -> forPriority(organizationId, p)).toList();
    }

    /** Aplica a los tickets nuevos y a los que cambien de prioridad; los relojes en curso no se tocan. */
    @Transactional
    public Policy update(UUID organizationId, AppUser actor, TicketPriority priority, int responseMinutes,
                         int resolutionMinutes, boolean businessHours) {
        access.require(organizationId, actor, Role.ADMIN);
        SlaPolicy policy = policies.findByOrganizationIdAndPriority(organizationId, priority)
            .orElseGet(() -> new SlaPolicy(organizationId, priority));
        policy.change(responseMinutes, resolutionMinutes, businessHours);
        return view(policies.save(policy));
    }

    private static Policy view(SlaPolicy policy) {
        return new Policy(policy.getPriority(), policy.getResponseMinutes(), policy.getResolutionMinutes(),
            policy.isBusinessHours());
    }
}
