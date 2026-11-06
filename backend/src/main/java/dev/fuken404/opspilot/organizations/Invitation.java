package dev.fuken404.opspilot.organizations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Invitación a alguien que aún no ha entrado nunca. Se acepta sola en su primer inicio de sesión. */
@Entity
@Table(name = "invitation")
class Invitation {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false, updatable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Invitation() {
    }

    Invitation(UUID organizationId, String email, Role role, Instant now) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.email = email.toLowerCase();
        this.role = role;
        this.createdAt = now;
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    String getEmail() {
        return email;
    }

    Role getRole() {
        return role;
    }
}
