package dev.fuken404.opspilot.organizations;

import dev.fuken404.opspilot.identity.AppUser;
import dev.fuken404.opspilot.shared.NotFoundException;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Única puerta para comprobar permisos dentro de una organización. Quien no es miembro recibe 404:
 * así no puede saber si la organización existe.
 */
@Component
public class OrganizationAccess {

    private final MembershipRepository memberships;

    OrganizationAccess(MembershipRepository memberships) {
        this.memberships = memberships;
    }

    public Role require(UUID organizationId, AppUser user, Role... allowed) {
        Role role = memberships.findByOrganizationIdAndUserId(organizationId, user.getId())
            .map(Membership::getRole)
            .orElseThrow(() -> new NotFoundException("Organización no encontrada."));
        if (allowed.length > 0 && Arrays.stream(allowed).noneMatch(role::equals)) {
            throw new AccessDeniedException("Rol insuficiente");
        }
        return role;
    }
}
