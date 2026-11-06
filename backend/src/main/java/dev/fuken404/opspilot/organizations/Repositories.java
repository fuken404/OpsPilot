package dev.fuken404.opspilot.organizations;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findBySlug(String slug);

    /** Bloquea la organización mientras dura la transacción: serializa cambios sobre sus administradores. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Organization o where o.id = :id")
    Optional<Organization> lockById(UUID id);
}

interface MembershipRepository extends JpaRepository<Membership, UUID> {

    Optional<Membership> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    List<Membership> findByUserId(UUID userId);

    List<Membership> findByOrganizationId(UUID organizationId);

    long countByOrganizationIdAndRole(UUID organizationId, Role role);

    Optional<Membership> findByIdAndOrganizationId(UUID id, UUID organizationId);
}

interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    List<Invitation> findByEmail(String email);

    List<Invitation> findByOrganizationId(UUID organizationId);

    boolean existsByOrganizationIdAndEmail(UUID organizationId, String email);
}
