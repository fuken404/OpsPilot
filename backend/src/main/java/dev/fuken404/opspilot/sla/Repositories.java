package dev.fuken404.opspilot.sla;

import dev.fuken404.opspilot.incidents.TicketPriority;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SlaPolicyRepository extends JpaRepository<SlaPolicy, UUID> {

    Optional<SlaPolicy> findByOrganizationIdAndPriority(UUID organizationId, TicketPriority priority);

    List<SlaPolicy> findByOrganizationId(UUID organizationId);
}

interface SlaClockRepository extends JpaRepository<SlaClock, UUID> {

    List<SlaClock> findByTicketId(UUID ticketId);

    List<SlaClock> findByOrganizationIdAndMetAtIsNullAndStoppedAtIsNull(UUID organizationId);

    /**
     * Relojes que el barrido debe atender. SKIP LOCKED: si dos barridos corren a la vez, cada reloj
     * lo procesa uno solo, sin que el otro espere.
     */
    @Query(nativeQuery = true, value = """
        select * from sla_clock
        where organization_id = :organizationId
          and met_at is null and stopped_at is null and paused_since is null
          and ((breached_at is null and due_at <= :now) or (warned_at is null and warn_at <= :now))
        order by due_at
        for update skip locked""")
    List<SlaClock> lockPending(@Param("organizationId") UUID organizationId, @Param("now") Instant now);
}
