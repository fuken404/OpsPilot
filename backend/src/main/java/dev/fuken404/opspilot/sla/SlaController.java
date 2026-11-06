package dev.fuken404.opspilot.sla;

import dev.fuken404.opspilot.identity.Users;
import dev.fuken404.opspilot.incidents.TicketPriority;
import dev.fuken404.opspilot.shared.InternalJobs;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SlaController {

    private final SlaStatus status;
    private final SlaPolicies policies;
    private final SlaSweeper sweeper;
    private final InternalJobs jobs;
    private final Users users;

    SlaController(SlaStatus status, SlaPolicies policies, SlaSweeper sweeper, InternalJobs jobs, Users users) {
        this.status = status;
        this.policies = policies;
        this.sweeper = sweeper;
        this.jobs = jobs;
        this.users = users;
    }

    record PolicyChange(@NotNull @Min(1) @Max(100_000) Integer responseMinutes,
                        @NotNull @Min(1) @Max(1_000_000) Integer resolutionMinutes, boolean businessHours) {
    }

    @GetMapping("/api/organizations/{organizationId}/tickets/{ticketId}/sla")
    List<SlaStatus.View> ticket(@PathVariable UUID organizationId, @PathVariable UUID ticketId,
                                @AuthenticationPrincipal Jwt jwt) {
        return status.forTicket(organizationId, users.current(jwt), ticketId);
    }

    @GetMapping("/api/organizations/{organizationId}/sla/open")
    List<SlaStatus.View> open(@PathVariable UUID organizationId, @AuthenticationPrincipal Jwt jwt) {
        return status.open(organizationId, users.current(jwt));
    }

    @GetMapping("/api/organizations/{organizationId}/sla/policies")
    List<SlaPolicies.Policy> policies(@PathVariable UUID organizationId, @AuthenticationPrincipal Jwt jwt) {
        return policies.list(organizationId, users.current(jwt));
    }

    @PutMapping("/api/organizations/{organizationId}/sla/policies/{priority}")
    SlaPolicies.Policy updatePolicy(@PathVariable UUID organizationId, @PathVariable TicketPriority priority,
                                    @Valid @RequestBody PolicyChange body, @AuthenticationPrincipal Jwt jwt) {
        return policies.update(organizationId, users.current(jwt), priority, body.responseMinutes(),
            body.resolutionMinutes(), body.businessHours());
    }

    @PostMapping("/api/internal/sla/sweep")
    ResponseEntity<SlaSweeper.Result> sweep(@RequestHeader(name = "X-Internal-Token", required = false) String token) {
        return jobs.run(token, sweeper::sweep);
    }
}
