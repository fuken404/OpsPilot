package dev.fuken404.opspilot.incidents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket")
class Ticket {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false, updatable = false)
    private int number;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @Column(name = "requester_id", nullable = false, updatable = false)
    private UUID requesterId;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    /** Bloqueo optimista: dos personas que editan la misma versión no se pisan; la segunda recibe 409. */
    @Version
    private long version;

    protected Ticket() {
    }

    Ticket(UUID organizationId, int number, String title, String description, TicketCategory category,
           TicketPriority priority, UUID requesterId, Instant now) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.number = number;
        this.title = title;
        this.description = description;
        this.category = category;
        this.priority = priority;
        this.status = TicketStatus.NEW;
        this.requesterId = requesterId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void moveTo(TicketStatus target, Instant now) {
        status = target;
        resolvedAt = target == TicketStatus.RESOLVED ? now : target == TicketStatus.CLOSED ? resolvedAt : null;
        closedAt = target.isFinal() ? now : null;
        updatedAt = now;
    }

    void assignTo(UUID assigneeId, Instant now) {
        this.assigneeId = assigneeId;
        updatedAt = now;
    }

    void triage(TicketPriority priority, TicketCategory category, Instant now) {
        this.priority = priority;
        this.category = category;
        updatedAt = now;
    }

    UUID getId() {
        return id;
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    int getNumber() {
        return number;
    }

    String getTitle() {
        return title;
    }

    String getDescription() {
        return description;
    }

    TicketCategory getCategory() {
        return category;
    }

    TicketPriority getPriority() {
        return priority;
    }

    TicketStatus getStatus() {
        return status;
    }

    UUID getRequesterId() {
        return requesterId;
    }

    UUID getAssigneeId() {
        return assigneeId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    Instant getResolvedAt() {
        return resolvedAt;
    }

    Instant getClosedAt() {
        return closedAt;
    }

    long getVersion() {
        return version;
    }
}
