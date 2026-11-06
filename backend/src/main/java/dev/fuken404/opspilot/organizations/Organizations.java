package dev.fuken404.opspilot.organizations;

import dev.fuken404.opspilot.identity.AppUser;
import dev.fuken404.opspilot.identity.UserProvisioned;
import dev.fuken404.opspilot.identity.Users;
import dev.fuken404.opspilot.shared.ConflictException;
import dev.fuken404.opspilot.shared.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Organizations {

    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final InvitationRepository invitations;
    private final OrganizationAccess access;
    private final Users users;
    private final Clock clock;

    Organizations(OrganizationRepository organizations, MembershipRepository memberships,
                  InvitationRepository invitations, OrganizationAccess access, Users users, Clock clock) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.invitations = invitations;
        this.access = access;
        this.users = users;
        this.clock = clock;
    }

    public record MyMembership(UUID organizationId, String name, String slug, Role role) {
    }

    public record Member(UUID membershipId, UUID userId, String email, String displayName, Role role, boolean pending) {
    }

    @Transactional(readOnly = true)
    public List<MyMembership> membershipsOf(AppUser user) {
        List<Membership> mine = memberships.findByUserId(user.getId());
        Map<UUID, Organization> byId = organizations.findAllById(mine.stream().map(Membership::getOrganizationId).toList())
            .stream().collect(Collectors.toMap(Organization::getId, Function.identity()));
        return mine.stream()
            .map(m -> new MyMembership(m.getOrganizationId(), byId.get(m.getOrganizationId()).getName(),
                byId.get(m.getOrganizationId()).getSlug(), m.getRole()))
            .sorted(Comparator.comparing(MyMembership::name))
            .toList();
    }

    /** Para procesos de sistema que recorren todas las organizaciones, como el barrido de SLA. */
    @Transactional(readOnly = true)
    public List<UUID> allIds() {
        return organizations.findAll().stream().map(Organization::getId).toList();
    }

    @Transactional(readOnly = true)
    public List<Member> members(UUID organizationId, AppUser actor) {
        access.require(organizationId, actor, Role.ADMIN, Role.TECHNICIAN);
        List<Member> active = memberships.findByOrganizationId(organizationId).stream()
            .map(m -> {
                AppUser user = users.findById(m.getUserId()).orElseThrow();
                return new Member(m.getId(), user.getId(), user.getEmail(), user.getDisplayName(), m.getRole(), false);
            })
            .toList();
        List<Member> pending = invitations.findByOrganizationId(organizationId).stream()
            .map(i -> new Member(null, null, i.getEmail(), i.getEmail(), i.getRole(), true))
            .toList();
        return java.util.stream.Stream.concat(active.stream(), pending.stream())
            .sorted(Comparator.comparing(Member::email))
            .toList();
    }

    /** Agrega a alguien que ya entró alguna vez, o deja una invitación para su primer inicio de sesión. */
    @Transactional
    public Member add(UUID organizationId, AppUser actor, String email, Role role) {
        access.require(organizationId, actor, Role.ADMIN);
        String normalized = email.trim().toLowerCase();
        var existing = users.findByEmail(normalized);
        if (existing.isPresent()) {
            AppUser user = existing.get();
            if (memberships.findByOrganizationIdAndUserId(organizationId, user.getId()).isPresent()) {
                throw new ConflictException("Esa persona ya es miembro.");
            }
            Membership saved = memberships.save(new Membership(organizationId, user.getId(), role, now()));
            return new Member(saved.getId(), user.getId(), user.getEmail(), user.getDisplayName(), role, false);
        }
        if (invitations.existsByOrganizationIdAndEmail(organizationId, normalized)) {
            throw new ConflictException("Ya hay una invitación para ese correo.");
        }
        invitations.save(new Invitation(organizationId, normalized, role, now()));
        return new Member(null, null, normalized, normalized, role, true);
    }

    /**
     * Cambia el rol de un miembro. La organización queda bloqueada durante la transacción: si dos
     * administradores se quitan el rol a la vez, el segundo ve el resultado del primero y la
     * organización nunca se queda sin administradores.
     */
    @Transactional
    public void changeRole(UUID organizationId, AppUser actor, UUID membershipId, Role role) {
        access.require(organizationId, actor, Role.ADMIN);
        organizations.lockById(organizationId).orElseThrow(() -> new NotFoundException("Organización no encontrada."));
        Membership target = memberships.findByIdAndOrganizationId(membershipId, organizationId)
            .orElseThrow(() -> new NotFoundException("Miembro no encontrado."));
        if (target.getRole() == Role.ADMIN && role != Role.ADMIN) {
            ensureAnotherAdmin(organizationId);
        }
        target.changeRole(role);
    }

    @Transactional
    public void remove(UUID organizationId, AppUser actor, UUID membershipId) {
        access.require(organizationId, actor, Role.ADMIN);
        organizations.lockById(organizationId).orElseThrow(() -> new NotFoundException("Organización no encontrada."));
        Membership target = memberships.findByIdAndOrganizationId(membershipId, organizationId)
            .orElseThrow(() -> new NotFoundException("Miembro no encontrado."));
        if (target.getRole() == Role.ADMIN) {
            ensureAnotherAdmin(organizationId);
        }
        memberships.delete(target);
    }

    private void ensureAnotherAdmin(UUID organizationId) {
        if (memberships.countByOrganizationIdAndRole(organizationId, Role.ADMIN) <= 1) {
            throw new ConflictException("La organización necesita al menos un administrador.");
        }
    }

    /** Solo con correo verificado: si no, cualquiera podría registrarse con un correo ajeno y heredar su invitación. */
    @EventListener
    void acceptInvitations(UserProvisioned event) {
        if (!event.emailVerified() || event.email() == null) {
            return;
        }
        for (Invitation invitation : invitations.findByEmail(event.email().toLowerCase())) {
            memberships.save(new Membership(invitation.getOrganizationId(), event.userId(), invitation.getRole(), now()));
            invitations.delete(invitation);
        }
    }

    /** Para datos de demostración y pruebas. */
    @Transactional
    public UUID createWithInvitations(String name, String slug, Map<String, Role> invitationsByEmail) {
        Organization organization = organizations.findBySlug(slug)
            .orElseGet(() -> organizations.save(new Organization(name, slug, now())));
        invitationsByEmail.forEach((email, role) -> {
            var user = users.findByEmail(email);
            if (user.isPresent()) {
                if (memberships.findByOrganizationIdAndUserId(organization.getId(), user.get().getId()).isEmpty()) {
                    memberships.save(new Membership(organization.getId(), user.get().getId(), role, now()));
                }
            } else if (!invitations.existsByOrganizationIdAndEmail(organization.getId(), email.toLowerCase())) {
                invitations.save(new Invitation(organization.getId(), email, role, now()));
            }
        });
        return organization.getId();
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
