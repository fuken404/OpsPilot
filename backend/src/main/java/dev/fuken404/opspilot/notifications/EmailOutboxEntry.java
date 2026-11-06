package dev.fuken404.opspilot.notifications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_outbox")
class EmailOutboxEntry {

    static final int MAX_ATTEMPTS = 8;

    enum Status { PENDING, SENT, FAILED }

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "ticket_id", updatable = false)
    private UUID ticketId;

    @Column(nullable = false, updatable = false)
    private String recipient;

    @Column(nullable = false, updatable = false)
    private String subject;

    @Column(nullable = false, updatable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected EmailOutboxEntry() {
    }

    EmailOutboxEntry(UUID organizationId, UUID ticketId, String recipient, String subject, String body, Instant now) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.ticketId = ticketId;
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
        this.status = Status.PENDING;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    void sent(Instant now) {
        status = Status.SENT;
        attempts++;
        sentAt = now;
        lastError = null;
    }

    /** Espera creciente: 1, 2, 4, 8… minutos (máximo una hora). Tras 8 intentos se da por fallido. */
    void failed(Instant now, String error) {
        attempts++;
        lastError = error == null ? "Error desconocido" : error.substring(0, Math.min(error.length(), 500));
        if (attempts >= MAX_ATTEMPTS) {
            status = Status.FAILED;
        } else {
            nextAttemptAt = now.plus(Duration.ofMinutes(Math.min(60, 1L << (attempts - 1))));
        }
    }

    String getRecipient() {
        return recipient;
    }

    String getSubject() {
        return subject;
    }

    String getBody() {
        return body;
    }

    Status getStatus() {
        return status;
    }

    int getAttempts() {
        return attempts;
    }
}
