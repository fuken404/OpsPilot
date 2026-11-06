package dev.fuken404.opspilot.organizations;

import dev.fuken404.opspilot.identity.AppUser;
import dev.fuken404.opspilot.identity.Users;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class OrganizationsController {

    private final Organizations organizations;
    private final Users users;

    OrganizationsController(Organizations organizations, Users users) {
        this.organizations = organizations;
        this.users = users;
    }

    record Me(UUID id, String email, String displayName, List<Organizations.MyMembership> memberships) {
    }

    record NewMember(@NotBlank @Email @Size(max = 200) String email, @NotNull Role role) {
    }

    record RoleChange(@NotNull Role role) {
    }

    @GetMapping("/me")
    Me me(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = users.current(jwt);
        return new Me(user.getId(), user.getEmail(), user.getDisplayName(), organizations.membershipsOf(user));
    }

    @GetMapping("/organizations/{organizationId}/members")
    List<Organizations.Member> members(@PathVariable UUID organizationId, @AuthenticationPrincipal Jwt jwt) {
        return organizations.members(organizationId, users.current(jwt));
    }

    @PostMapping("/organizations/{organizationId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    Organizations.Member add(@PathVariable UUID organizationId, @Valid @RequestBody NewMember body,
                             @AuthenticationPrincipal Jwt jwt) {
        return organizations.add(organizationId, users.current(jwt), body.email(), body.role());
    }

    @PatchMapping("/organizations/{organizationId}/members/{membershipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changeRole(@PathVariable UUID organizationId, @PathVariable UUID membershipId,
                    @Valid @RequestBody RoleChange body, @AuthenticationPrincipal Jwt jwt) {
        organizations.changeRole(organizationId, users.current(jwt), membershipId, body.role());
    }

    @DeleteMapping("/organizations/{organizationId}/members/{membershipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable UUID organizationId, @PathVariable UUID membershipId, @AuthenticationPrincipal Jwt jwt) {
        organizations.remove(organizationId, users.current(jwt), membershipId);
    }
}
