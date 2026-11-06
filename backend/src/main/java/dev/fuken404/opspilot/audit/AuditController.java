package dev.fuken404.opspilot.audit;

import dev.fuken404.opspilot.identity.Users;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AuditController {

    private final AuditLog audit;
    private final Users users;

    AuditController(AuditLog audit, Users users) {
        this.audit = audit;
        this.users = users;
    }

    @GetMapping("/api/organizations/{organizationId}/tickets/{ticketId}/history")
    List<AuditLog.Entry> ticketHistory(@PathVariable UUID organizationId, @PathVariable UUID ticketId,
                                       @AuthenticationPrincipal Jwt jwt) {
        return audit.ticketHistory(organizationId, users.current(jwt), ticketId);
    }
}
