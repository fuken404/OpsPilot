package dev.fuken404.opspilot.incidents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_attachment")
class TicketAttachment {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "ticket_id", nullable = false, updatable = false)
    private UUID ticketId;

    @Column(name = "uploader_id", nullable = false, updatable = false)
    private UUID uploaderId;

    @Column(nullable = false, updatable = false)
    private String filename;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(nullable = false, updatable = false)
    private String sha256;

    @Column(name = "storage_key", nullable = false, updatable = false)
    private String storageKey;

    @Column(nullable = false, updatable = false)
    private boolean internal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TicketAttachment() {
    }

    TicketAttachment(Ticket ticket, UUID uploaderId, String filename, String contentType, long sizeBytes, String sha256,
                     boolean internal, Instant now) {
        this.id = UUID.randomUUID();
        this.organizationId = ticket.getOrganizationId();
        this.ticketId = ticket.getId();
        this.uploaderId = uploaderId;
        this.filename = filename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.storageKey = organizationId + "/" + ticketId + "/" + id;
        this.internal = internal;
        this.createdAt = now;
    }

    UUID getId() {
        return id;
    }

    UUID getTicketId() {
        return ticketId;
    }

    UUID getUploaderId() {
        return uploaderId;
    }

    String getFilename() {
        return filename;
    }

    String getContentType() {
        return contentType;
    }

    long getSizeBytes() {
        return sizeBytes;
    }

    String getStorageKey() {
        return storageKey;
    }

    boolean isInternal() {
        return internal;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
