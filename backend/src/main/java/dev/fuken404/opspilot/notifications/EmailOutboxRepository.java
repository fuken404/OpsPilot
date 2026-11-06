package dev.fuken404.opspilot.notifications;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EmailOutboxRepository extends JpaRepository<EmailOutboxEntry, UUID> {

    /** SKIP LOCKED: dos despachadores a la vez nunca mandan el mismo correo. */
    @Query(nativeQuery = true, value = """
        select * from email_outbox
        where organization_id = :organizationId and status = 'PENDING' and next_attempt_at <= :now
        order by created_at
        limit 50
        for update skip locked""")
    List<EmailOutboxEntry> lockDue(@Param("organizationId") UUID organizationId, @Param("now") Instant now);
}
