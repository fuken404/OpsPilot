package dev.fuken404.opspilot.dashboard;

import dev.fuken404.opspilot.identity.Users;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DashboardController {

    private final Dashboards dashboards;
    private final Users users;

    DashboardController(Dashboards dashboards, Users users) {
        this.dashboards = dashboards;
        this.users = users;
    }

    @GetMapping("/api/organizations/{organizationId}/dashboard")
    Dashboards.Dashboard dashboard(@PathVariable UUID organizationId, @AuthenticationPrincipal Jwt jwt) {
        return dashboards.of(organizationId, users.current(jwt));
    }
}
