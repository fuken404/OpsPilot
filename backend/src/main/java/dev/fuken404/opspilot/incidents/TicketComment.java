package dev.fuken404.opspilot.incidents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_comment")
class TicketComment {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "ticket_id", nullable = false, updatable = false)
    private UUID ticketId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(nullable = false, updatable = false)
    private String body;

    /** Nota del equipo: el cliente nunca la recibe. */
    @Column(nullable = false, updatable = false)
    private boolean internal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TicketComment() {
    }

    TicketComment(Ticket ticket, UUID authorId, String body, boolean internal, Instant now) {
        this.id = UUID.randomUUID();
        this.organizationId = ticket.getOrganizationId();
        this.ticketId = ticket.getId();
        this.authorId = authorId;
        this.body = body;
        this.internal = internal;
        this.createdAt = now;
    }

    UUID getId() {
        return id;
    }

    UUID getAuthorId() {
        return authorId;
    }

    String getBody() {
        return body;
    }

    boolean isInternal() {
        return internal;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
