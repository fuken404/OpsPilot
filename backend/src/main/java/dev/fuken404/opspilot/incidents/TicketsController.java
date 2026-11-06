package dev.fuken404.opspilot.incidents;

import dev.fuken404.opspilot.identity.Users;
import jakarta.validation.Valid;
import java.io.IOException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.nio.charset.StandardCharsets;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/organizations/{organizationId}")
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

    @GetMapping("/tickets")
    List<Tickets.Summary> list(@PathVariable UUID organizationId, @AuthenticationPrincipal Jwt jwt,
                               @RequestParam(defaultValue = "OPEN") Tickets.View view,
                               @RequestParam(defaultValue = "ANY") Tickets.AssigneeFilter assignee,
                               @RequestParam(required = false) @Size(max = 100) String q) {
        return tickets.list(organizationId, users.current(jwt), new Tickets.Filter(view, assignee, q));
    }

    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    Tickets.Detail create(@PathVariable UUID organizationId, @Valid @RequestBody NewTicketBody body,
                          @AuthenticationPrincipal Jwt jwt) {
        return tickets.create(organizationId, users.current(jwt),
            new Tickets.NewTicket(body.title(), body.description(), body.category(), body.priority()));
    }

    @GetMapping("/tickets/{ticketId}")
    Tickets.Detail get(@PathVariable UUID organizationId, @PathVariable UUID ticketId, @AuthenticationPrincipal Jwt jwt) {
        return tickets.get(organizationId, users.current(jwt), ticketId);
    }

    @PutMapping("/tickets/{ticketId}/status")
    Tickets.Detail changeStatus(@PathVariable UUID organizationId, @PathVariable UUID ticketId,
                                @Valid @RequestBody StatusChange body, @AuthenticationPrincipal Jwt jwt) {
        return tickets.changeStatus(organizationId, users.current(jwt), ticketId, body.version(), body.status());
    }

    @PutMapping("/tickets/{ticketId}/assignee")
    Tickets.Detail assign(@PathVariable UUID organizationId, @PathVariable UUID ticketId,
                          @Valid @RequestBody Assignment body, @AuthenticationPrincipal Jwt jwt) {
        return tickets.assign(organizationId, users.current(jwt), ticketId, body.version(), body.assigneeId());
    }

    @PatchMapping("/tickets/{ticketId}")
    Tickets.Detail triage(@PathVariable UUID organizationId, @PathVariable UUID ticketId,
                          @Valid @RequestBody Triage body, @AuthenticationPrincipal Jwt jwt) {
        return tickets.triage(organizationId, users.current(jwt), ticketId, body.version(), body.priority(),
            body.category());
    }

    @PostMapping("/tickets/{ticketId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    Tickets.Detail comment(@PathVariable UUID organizationId, @PathVariable UUID ticketId,
                           @Valid @RequestBody NewComment body, @AuthenticationPrincipal Jwt jwt) {
        return tickets.comment(organizationId, users.current(jwt), ticketId, body.body(), body.internal());
    }

    @PostMapping(path = "/tickets/{ticketId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    Tickets.Detail attach(@PathVariable UUID organizationId, @PathVariable UUID ticketId,
                          @RequestPart("file") MultipartFile file,
                          @RequestParam(name = "internal", defaultValue = "false") boolean internal,
                          @AuthenticationPrincipal Jwt jwt) throws IOException {
        return tickets.attach(organizationId, users.current(jwt), ticketId, file.getOriginalFilename(), file.getBytes(),
            internal);
    }

    /** Siempre como descarga y sin que el navegador adivine el tipo: un archivo nunca se ejecuta en la página. */
    @GetMapping("/attachments/{attachmentId}")
    ResponseEntity<InputStreamResource> download(@PathVariable UUID organizationId, @PathVariable UUID attachmentId,
                                                 @AuthenticationPrincipal Jwt jwt) {
        Tickets.Download file = tickets.download(organizationId, users.current(jwt), attachmentId);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.contentType()))
            .contentLength(file.sizeBytes())
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .header("Content-Security-Policy", "default-src 'none'; sandbox")
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(new InputStreamResource(file.content()));
    }
}
