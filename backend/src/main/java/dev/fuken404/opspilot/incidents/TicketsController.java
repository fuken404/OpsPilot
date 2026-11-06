package dev.fuken404.opspilot.incidents;

import dev.fuken404.opspilot.identity.Users;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations/{organizationId}/tickets")
class TicketsController {

    private final Tickets tickets;
    private final Users users;

    TicketsController(Tickets tickets, Users users) {
        this.tickets = tickets;
        this.users = users;
    }

    record NewTicketBody(@NotBlank @Size(min = 3, max = 200) String title, @NotBlank @Size(max = 10000) String description,
                         @NotNull TicketCategory category, TicketPriority priority) {
    }

    /** Toda modificación lleva la versión que la persona tenía en pantalla. */
    record StatusChange(@NotNull Long version, @NotNull TicketStatus status) {
    }

    record Assignment(@NotNull Long version, UUID assigneeId) {
    }

    record Triage(@NotNull Long version, @NotNull TicketPriority priority, @NotNull TicketCategory category) {
    }

    record NewComment(@NotBlank @Size(max = 5000) String body, boolean internal) {
    }

    @GetMapping
    List<Tickets.Summary> list(@PathVariable UUID organizationId, @AuthenticationPrincipal Jwt jwt,
                               @RequestParam(defaultValue = "OPEN") Tickets.View view,
                               @RequestParam(defaultValue = "ANY") Tickets.AssigneeFilter assignee,
                               @RequestParam(required = false) @Size(max = 100) String q) {
        return tickets.list(organizationId, users.current(jwt), new Tickets.Filter(view, assignee, q));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Tickets.Detail create(@PathVariable UUID organizationId, @Valid @RequestBody NewTicketBody body,
                          @AuthenticationPrincipal Jwt jwt) {
        return tickets.create(organizationId, users.current(jwt),
            new Tickets.NewTicket(body.title(), body.description(), body.category(), body.priority()));
    }

    @GetMapping("/{ticketId}")
    Tickets.Detail get(@PathVariable UUID organizationId, @PathVariable UUID ticketId, @AuthenticationPrincipal Jwt jwt) {
        return tickets.get(organizationId, users.current(jwt), ticketId);
    }

    @PutMapping("/{ticketId}/status")
    Tickets.Detail changeStatus(@PathVariable UUID organizationId, @PathVariable UUID ticketId,
                                @Valid @RequestBody StatusChange body, @AuthenticationPrincipal Jwt jwt) {
        return tickets.changeStatus(organizationId, users.current(jwt), ticketId, body.version(), body.status());
    }

    @PutMapping("/{ticketId}/assignee")
    Tickets.Detail assign(@PathVariable UUID organizationId, @PathVariable UUID ticketId,
                          @Valid @RequestBody Assignment body, @AuthenticationPrincipal Jwt jwt) {
        return tickets.assign(organizationId, users.current(jwt), ticketId, body.version(), body.assigneeId());
    }

    @PatchMapping("/{ticketId}")
    Tickets.Detail triage(@PathVariable UUID organizationId, @PathVariable UUID ticketId,
                          @Valid @RequestBody Triage body, @AuthenticationPrincipal Jwt jwt) {
        return tickets.triage(organizationId, users.current(jwt), ticketId, body.version(), body.priority(),
            body.category());
    }

    @PostMapping("/{ticketId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    Tickets.Detail comment(@PathVariable UUID organizationId, @PathVariable UUID ticketId,
                           @Valid @RequestBody NewComment body, @AuthenticationPrincipal Jwt jwt) {
        return tickets.comment(organizationId, users.current(jwt), ticketId, body.body(), body.internal());
    }
}
