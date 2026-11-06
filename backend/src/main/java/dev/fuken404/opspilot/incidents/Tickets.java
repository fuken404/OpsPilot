package dev.fuken404.opspilot.incidents;

import dev.fuken404.opspilot.identity.AppUser;
import dev.fuken404.opspilot.identity.Users;
import dev.fuken404.opspilot.incidents.TicketActivity.Action;
import dev.fuken404.opspilot.organizations.OrganizationAccess;
import dev.fuken404.opspilot.organizations.Role;
import dev.fuken404.opspilot.shared.ConflictException;
import dev.fuken404.opspilot.shared.FileStorage;
import dev.fuken404.opspilot.shared.NotFoundException;
import dev.fuken404.opspilot.shared.RejectedFileException;
import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class Tickets {

    private static final int LIST_LIMIT = 200;

    private final TicketRepository tickets;
    private final TicketCommentRepository comments;
    private final TicketAttachmentRepository attachments;
    private final FileStorage storage;
    private final long maxAttachmentBytes;
    private final OrganizationAccess access;
    private final Users users;
    private final ApplicationEventPublisher events;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    Tickets(TicketRepository tickets, TicketCommentRepository comments, TicketAttachmentRepository attachments,
            FileStorage storage, @Value("${opspilot.attachments.max-bytes}") long maxAttachmentBytes,
            OrganizationAccess access, Users users, ApplicationEventPublisher events, JdbcTemplate jdbc, Clock clock) {
        this.tickets = tickets;
        this.comments = comments;
        this.attachments = attachments;
        this.storage = storage;
        this.maxAttachmentBytes = maxAttachmentBytes;
        this.access = access;
        this.users = users;
        this.events = events;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record Person(UUID id, String displayName, String email) {
    }

    public record Summary(UUID id, int number, String title, TicketCategory category, TicketPriority priority,
                          TicketStatus status, Person requester, Person assignee, Instant createdAt, Instant updatedAt) {
    }

    public record Comment(UUID id, Person author, String body, boolean internal, Instant createdAt) {
    }

    public record Attachment(UUID id, String filename, String contentType, long sizeBytes, Person uploader,
                             boolean internal, Instant createdAt) {
    }

    /** Lo que se descarga. Quien lo recibe debe cerrar el stream. */
    public record Download(String filename, String contentType, long sizeBytes, InputStream content) {
    }

    /** Datos básicos para procesos del sistema (correos). Requiere la organización activa. */
    public record Snapshot(UUID id, int number, String title, TicketStatus status, UUID requesterId, UUID assigneeId) {
    }

    /** El detalle incluye lo que la persona puede hacer, para que la interfaz no adivine permisos. */
    public record Detail(UUID id, int number, String title, String description, TicketCategory category,
                         TicketPriority priority, TicketStatus status, Person requester, Person assignee,
                         Instant createdAt, Instant updatedAt, Instant resolvedAt, Instant closedAt, long version,
                         Set<TicketStatus> nextStatuses, boolean canTriage, boolean canAssign, boolean canComment,
                         boolean canCommentInternally, List<Comment> comments, List<Attachment> attachments) {
    }

    public enum View { OPEN, CLOSED, ALL }

    public enum AssigneeFilter { ANY, MINE, UNASSIGNED }

    public record Filter(View view, AssigneeFilter assignee, String query) {
    }

    public record NewTicket(String title, String description, TicketCategory category, TicketPriority priority) {
    }

    @Transactional(readOnly = true)
    public List<Summary> list(UUID organizationId, AppUser actor, Filter filter) {
        Role role = access.require(organizationId, actor);
        Specification<Ticket> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("organizationId"), organizationId));
            if (role == Role.CUSTOMER) {
                where.add(cb.equal(root.get("requesterId"), actor.getId()));
            }
            var finals = List.of(TicketStatus.CLOSED, TicketStatus.CANCELLED);
            switch (filter.view()) {
                case OPEN -> where.add(cb.not(root.get("status").in(finals)));
                case CLOSED -> where.add(root.get("status").in(finals));
                case ALL -> { }
            }
            switch (filter.assignee()) {
                case MINE -> where.add(cb.equal(root.get("assigneeId"), actor.getId()));
                case UNASSIGNED -> where.add(cb.isNull(root.get("assigneeId")));
                case ANY -> { }
            }
            if (filter.query() != null && !filter.query().isBlank()) {
                String like = "%" + filter.query().trim().toLowerCase()
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                where.add(cb.like(cb.lower(root.get("title")), like, '\\'));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        List<Ticket> found = tickets.findAll(spec, PageRequest.of(0, LIST_LIMIT, Sort.by("createdAt").descending()))
            .getContent();
        Map<UUID, AppUser> people = users.findAllById(found.stream()
            .flatMap(t -> Stream.of(t.getRequesterId(), t.getAssigneeId())).filter(Objects::nonNull).toList());
        return found.stream()
            .map(t -> new Summary(t.getId(), t.getNumber(), t.getTitle(), t.getCategory(), t.getPriority(),
                t.getStatus(), person(people, t.getRequesterId()), person(people, t.getAssigneeId()),
                t.getCreatedAt(), t.getUpdatedAt()))
            .toList();
    }

    /** El cliente siempre crea con prioridad media: la prioridad define el SLA y la decide el equipo. */
    @Transactional
    public Detail create(UUID organizationId, AppUser actor, NewTicket input) {
        Role role = access.require(organizationId, actor);
        TicketPriority priority = role == Role.CUSTOMER || input.priority() == null
            ? TicketPriority.MEDIUM : input.priority();
        Ticket ticket = tickets.save(new Ticket(organizationId, nextNumber(organizationId), input.title().trim(),
            input.description().trim(), input.category(), priority, actor.getId(), now()));
        publish(ticket, actor, Action.CREATED, Map.of("number", ticket.getNumber(), "priority", priority,
            "category", ticket.getCategory()));
        return detail(ticket, actor, role);
    }

    @Transactional(readOnly = true)
    public Detail get(UUID organizationId, AppUser actor, UUID ticketId) {
        Role role = access.require(organizationId, actor);
        return detail(load(organizationId, ticketId, actor, role), actor, role);
    }

    /** Para otros módulos: falla con 404 si la persona no puede ver ese ticket. */
    @Transactional(readOnly = true)
    public void requireVisible(UUID organizationId, AppUser actor, UUID ticketId) {
        load(organizationId, ticketId, actor, access.require(organizationId, actor));
    }

    @Transactional
    public Detail changeStatus(UUID organizationId, AppUser actor, UUID ticketId, long version, TicketStatus target) {
        Role role = access.require(organizationId, actor);
        Ticket ticket = load(organizationId, ticketId, actor, role);
        expectVersion(ticket, version);
        TicketStatus from = ticket.getStatus();
        if (!TicketWorkflow.exists(from, target)) {
            throw new ConflictException("Un ticket en " + from + " no puede pasar a " + target + ".");
        }
        if (!TicketWorkflow.allowed(from, role, isRequester(ticket, actor)).contains(target)) {
            throw new AccessDeniedException("Transición no permitida para el rol");
        }
        ticket.moveTo(target, now());
        tickets.flush();
        publish(ticket, actor, Action.STATUS_CHANGED, Map.of("from", from, "to", target));
        return detail(ticket, actor, role);
    }

    /**
     * El administrador asigna a cualquiera del equipo. El técnico toma tickets libres o reasigna los
     * suyos. Si dos técnicos toman el mismo ticket a la vez, el bloqueo optimista deja ganar a uno.
     */
    @Transactional
    public Detail assign(UUID organizationId, AppUser actor, UUID ticketId, long version, UUID assigneeId) {
        Role role = access.require(organizationId, actor, Role.ADMIN, Role.TECHNICIAN);
        Ticket ticket = load(organizationId, ticketId, actor, role);
        expectVersion(ticket, version);
        if (ticket.getStatus().isFinal()) {
            throw new ConflictException("El ticket ya está cerrado.");
        }
        if (role == Role.TECHNICIAN && !canTechnicianReassign(ticket, actor, assigneeId)) {
            throw new AccessDeniedException("El técnico solo toma tickets libres o reasigna los suyos");
        }
        if (assigneeId != null && access.roleOf(organizationId, assigneeId).filter(r -> r != Role.CUSTOMER).isEmpty()) {
            throw new ConflictException("Solo se puede asignar a técnicos o administradores de la organización.");
        }
        UUID from = ticket.getAssigneeId();
        if (Objects.equals(from, assigneeId)) {
            return detail(ticket, actor, role);
        }
        ticket.assignTo(assigneeId, now());
        tickets.flush();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", from);
        details.put("to", assigneeId);
        publish(ticket, actor, Action.ASSIGNED, details);
        return detail(ticket, actor, role);
    }

    @Transactional
    public Detail triage(UUID organizationId, AppUser actor, UUID ticketId, long version, TicketPriority priority,
                         TicketCategory category) {
        Role role = access.require(organizationId, actor, Role.ADMIN, Role.TECHNICIAN);
        Ticket ticket = load(organizationId, ticketId, actor, role);
        expectVersion(ticket, version);
        if (ticket.getStatus().isFinal()) {
            throw new ConflictException("El ticket ya está cerrado.");
        }
        Map<String, Object> details = new LinkedHashMap<>();
        if (ticket.getPriority() != priority) {
            details.put("priority", Map.of("from", ticket.getPriority(), "to", priority));
        }
        if (ticket.getCategory() != category) {
            details.put("category", Map.of("from", ticket.getCategory(), "to", category));
        }
        if (details.isEmpty()) {
            return detail(ticket, actor, role);
        }
        ticket.triage(priority, category, now());
        tickets.flush();
        publish(ticket, actor, Action.TRIAGED, details);
        return detail(ticket, actor, role);
    }

    /** Si el cliente responde mientras el equipo espera su respuesta, el ticket vuelve a estar en curso. */
    @Transactional
    public Detail comment(UUID organizationId, AppUser actor, UUID ticketId, String body, boolean internal) {
        Role role = access.require(organizationId, actor);
        Ticket ticket = load(organizationId, ticketId, actor, role);
        if (internal && role == Role.CUSTOMER) {
            throw new AccessDeniedException("El cliente no escribe notas internas");
        }
        if (ticket.getStatus().isFinal()) {
            throw new ConflictException("El ticket ya está cerrado.");
        }
        TicketComment saved = comments.save(new TicketComment(ticket, actor.getId(), body.trim(), internal, now()));
        publish(ticket, actor, Action.COMMENTED, Map.of("commentId", saved.getId(), "internal", internal));
        if (role == Role.CUSTOMER && ticket.getStatus() == TicketStatus.WAITING_CUSTOMER) {
            ticket.moveTo(TicketStatus.IN_PROGRESS, now());
            tickets.flush();
            publish(ticket, actor, Action.STATUS_CHANGED, Map.of("from", TicketStatus.WAITING_CUSTOMER,
                "to", TicketStatus.IN_PROGRESS, "reason", "CUSTOMER_REPLIED"));
        }
        return detail(ticket, actor, role);
    }

    private static boolean canAssign(Ticket ticket, AppUser actor, Role role) {
        return role == Role.ADMIN
            || role == Role.TECHNICIAN && (ticket.getAssigneeId() == null || actor.getId().equals(ticket.getAssigneeId()));
    }

    /**
     * Guarda el archivo y luego la fila. Si la transacción se deshace, el archivo se borra: no quedan
     * archivos huérfanos ocupando espacio.
     */
    @Transactional
    public Detail attach(UUID organizationId, AppUser actor, UUID ticketId, String originalName, byte[] content,
                         boolean internal) {
        Role role = access.require(organizationId, actor);
        Ticket ticket = load(organizationId, ticketId, actor, role);
        if (internal && role == Role.CUSTOMER) {
            throw new AccessDeniedException("El cliente no sube adjuntos internos");
        }
        if (ticket.getStatus().isFinal()) {
            throw new ConflictException("El ticket ya está cerrado.");
        }
        if (content.length == 0) {
            throw new RejectedFileException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "El archivo está vacío.");
        }
        if (content.length > maxAttachmentBytes) {
            throw new RejectedFileException(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo supera el máximo de 4 MB.");
        }
        String filename = FileTypes.cleanName(originalName);
        String contentType = FileTypes.detect(filename, content);
        TicketAttachment attachment = attachments.save(new TicketAttachment(ticket, actor.getId(), filename, contentType,
            content.length, sha256(content), internal, now()));
        try {
            storage.put(attachment.getStorageKey(), content, contentType);
        } catch (IOException error) {
            throw new UncheckedIOException("No se pudo guardar el archivo", error);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        storage.delete(attachment.getStorageKey());
                    } catch (IOException ignored) {
                        // queda huérfano; no afecta a nadie
                    }
                }
            }
        });
        publish(ticket, actor, Action.ATTACHED, Map.of("attachmentId", attachment.getId(), "filename", filename,
            "internal", internal));
        return detail(ticket, actor, role);
    }

    /** Los mismos permisos que ver el ticket; los adjuntos internos no existen para el cliente. */
    @Transactional(readOnly = true)
    public Download download(UUID organizationId, AppUser actor, UUID attachmentId) {
        Role role = access.require(organizationId, actor);
        TicketAttachment attachment = attachments.findByIdAndOrganizationId(attachmentId, organizationId)
            .filter(a -> role != Role.CUSTOMER || !a.isInternal())
            .orElseThrow(() -> new NotFoundException("Adjunto no encontrado."));
        load(organizationId, attachment.getTicketId(), actor, role);
        try {
            return new Download(attachment.getFilename(), attachment.getContentType(), attachment.getSizeBytes(),
                storage.open(attachment.getStorageKey()));
        } catch (IOException error) {
            throw new NotFoundException("El archivo ya no está disponible.");
        }
    }

    @Transactional(readOnly = true)
    public Optional<Snapshot> snapshot(UUID organizationId, UUID ticketId) {
        return tickets.findByIdAndOrganizationId(ticketId, organizationId)
            .map(t -> new Snapshot(t.getId(), t.getNumber(), t.getTitle(), t.getStatus(), t.getRequesterId(),
                t.getAssigneeId()));
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private boolean canTechnicianReassign(Ticket ticket, AppUser actor, UUID assigneeId) {
        boolean takingFreeTicket = ticket.getAssigneeId() == null && actor.getId().equals(assigneeId);
        boolean handingOverOwnTicket = actor.getId().equals(ticket.getAssigneeId());
        return takingFreeTicket || handingOverOwnTicket;
    }

    /** Un cliente solo ve sus tickets; los de otros responden 404, igual que los que no existen. */
    private Ticket load(UUID organizationId, UUID ticketId, AppUser actor, Role role) {
        return tickets.findByIdAndOrganizationId(ticketId, organizationId)
            .filter(t -> role != Role.CUSTOMER || isRequester(t, actor))
            .orElseThrow(() -> new NotFoundException("Ticket no encontrado."));
    }

    private static boolean isRequester(Ticket ticket, AppUser actor) {
        return ticket.getRequesterId().equals(actor.getId());
    }

    private static void expectVersion(Ticket ticket, long version) {
        if (ticket.getVersion() != version) {
            throw new ConflictException("El ticket cambió mientras lo veías. Recarga para ver la versión actual.");
        }
    }

    /** Numeración por organización sin huecos: el contador se bloquea hasta que termina la transacción. */
    private int nextNumber(UUID organizationId) {
        return jdbc.queryForObject("""
            insert into ticket_counter (organization_id, last_number) values (?, 1)
            on conflict (organization_id) do update set last_number = ticket_counter.last_number + 1
            returning last_number""", Integer.class, organizationId);
    }

    private void publish(Ticket ticket, AppUser actor, Action action, Map<String, Object> details) {
        Instant at = action == Action.CREATED ? ticket.getCreatedAt() : now();
        events.publishEvent(new TicketActivity(ticket.getOrganizationId(), ticket.getId(), actor.getId(), action, details,
            ticket.getStatus(), ticket.getPriority(), ticket.getRequesterId(), at));
    }

    private Detail detail(Ticket ticket, AppUser actor, Role role) {
        boolean staff = role != Role.CUSTOMER;
        List<TicketComment> visible = staff
            ? comments.findByTicketIdOrderByCreatedAt(ticket.getId())
            : comments.findByTicketIdAndInternalFalseOrderByCreatedAt(ticket.getId());
        Set<UUID> ids = new HashSet<>();
        ids.add(ticket.getRequesterId());
        if (ticket.getAssigneeId() != null) {
            ids.add(ticket.getAssigneeId());
        }
        visible.forEach(c -> ids.add(c.getAuthorId()));
        List<TicketAttachment> files = staff
            ? attachments.findByTicketIdOrderByCreatedAt(ticket.getId())
            : attachments.findByTicketIdAndInternalFalseOrderByCreatedAt(ticket.getId());
        files.forEach(a -> ids.add(a.getUploaderId()));
        Map<UUID, AppUser> people = users.findAllById(ids);
        boolean open = !ticket.getStatus().isFinal();
        return new Detail(ticket.getId(), ticket.getNumber(), ticket.getTitle(), ticket.getDescription(),
            ticket.getCategory(), ticket.getPriority(), ticket.getStatus(), person(people, ticket.getRequesterId()),
            person(people, ticket.getAssigneeId()), ticket.getCreatedAt(), ticket.getUpdatedAt(),
            ticket.getResolvedAt(), ticket.getClosedAt(), ticket.getVersion(),
            TicketWorkflow.allowed(ticket.getStatus(), role, isRequester(ticket, actor)),
            staff && open, open && canAssign(ticket, actor, role), open, staff && open,
            visible.stream().map(c -> new Comment(c.getId(), person(people, c.getAuthorId()), c.getBody(),
                c.isInternal(), c.getCreatedAt())).toList(),
            files.stream().map(a -> new Attachment(a.getId(), a.getFilename(), a.getContentType(), a.getSizeBytes(),
                person(people, a.getUploaderId()), a.isInternal(), a.getCreatedAt())).toList());
    }

    private static Person person(Map<UUID, AppUser> people, UUID id) {
        AppUser user = id == null ? null : people.get(id);
        return user == null ? null : new Person(user.getId(), user.getDisplayName(), user.getEmail());
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
