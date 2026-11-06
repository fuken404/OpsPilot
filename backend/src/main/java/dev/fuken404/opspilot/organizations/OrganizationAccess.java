package dev.fuken404.opspilot.organizations;

import dev.fuken404.opspilot.identity.AppUser;
import dev.fuken404.opspilot.shared.NotFoundException;
import dev.fuken404.opspilot.shared.TenantContext;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Única puerta para comprobar permisos dentro de una organización. Quien no es miembro recibe 404:
 * así no puede saber si la organización existe. Al pasar, deja la organización activa en la
 * transacción para que Row Level Security filtre el resto de consultas.
 */
@Component
public class OrganizationAccess {

    private final MembershipRepository memberships;
    private final TenantContext tenant;

    OrganizationAccess(MembershipRepository memberships, TenantContext tenant) {
        this.memberships = memberships;
        this.tenant = tenant;
    }

    public Role require(UUID organizationId, AppUser user, Role... allowed) {
        Role role = memberships.findByOrganizationIdAndUserId(organizationId, user.getId())
            .map(Membership::getRole)
            .orElseThrow(() -> new NotFoundException("Organización no encontrada."));
        if (allowed.length > 0 && Arrays.stream(allowed).noneMatch(role::equals)) {
            throw new AccessDeniedException("Rol insuficiente");
        }
        tenant.enter(organizationId);
        return role;
    }

    public Optional<Role> roleOf(UUID organizationId, UUID userId) {
        return memberships.findByOrganizationIdAndUserId(organizationId, userId).map(Membership::getRole);
    }
}
