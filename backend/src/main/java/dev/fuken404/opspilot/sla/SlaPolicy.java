package dev.fuken404.opspilot.sla;

import dev.fuken404.opspilot.incidents.TicketPriority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "sla_policy")
class SlaPolicy {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TicketPriority priority;

    @Column(name = "response_minutes", nullable = false)
    private int responseMinutes;

    @Column(name = "resolution_minutes", nullable = false)
    private int resolutionMinutes;

    @Column(name = "business_hours", nullable = false)
    private boolean businessHours;

    protected SlaPolicy() {
    }

    SlaPolicy(UUID organizationId, TicketPriority priority) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.priority = priority;
    }

    void change(int responseMinutes, int resolutionMinutes, boolean businessHours) {
        this.responseMinutes = responseMinutes;
        this.resolutionMinutes = resolutionMinutes;
        this.businessHours = businessHours;
    }

    TicketPriority getPriority() {
        return priority;
    }

    int getResponseMinutes() {
        return responseMinutes;
    }

    int getResolutionMinutes() {
        return resolutionMinutes;
    }

    boolean isBusinessHours() {
        return businessHours;
    }
}
