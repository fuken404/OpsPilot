package dev.fuken404.opspilot.audit;

import dev.fuken404.opspilot.identity.AppUser;
import dev.fuken404.opspilot.identity.Users;
import dev.fuken404.opspilot.incidents.TicketActivity;
import dev.fuken404.opspilot.organizations.OrganizationAccess;
import dev.fuken404.opspilot.organizations.Role;
import dev.fuken404.opspilot.sla.SlaAlert;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditLog {

    private static final TypeReference<Map<String, Object>> DETAILS = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final OrganizationAccess access;
    private final Users users;
    private final Clock clock;

    AuditLog(JdbcTemplate jdbc, JsonMapper json, OrganizationAccess access, Users users, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.access = access;
        this.users = users;
        this.clock = clock;
    }

    public record Entry(UUID id, String action, Actor actor, Map<String, Object> details, Instant occurredAt) {
    }

    public record Actor(UUID id, String displayName) {
    }

    /** MANDATORY: si alguien publicara la actividad fuera de una transacción, falla en vez de auditar a medias. */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    void on(TicketActivity activity) {
        jdbc.update("""
                insert into audit_event (id, organization_id, actor_id, entity_type, entity_id, action, details, occurred_at)
                values (?, ?, ?, 'TICKET', ?, ?, ?::jsonb, ?)""",
            UUID.randomUUID(), activity.organizationId(), activity.actorId(), activity.ticketId(),
            activity.action().name(), json.writeValueAsString(activity.details()), Timestamp.from(Instant.now(clock)));
    }

    /** Alertas del barrido de SLA: no tienen autor, las genera el sistema. */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    void on(SlaAlert alert) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("kind", alert.kind());
        details.put("dueAt", alert.dueAt());
        jdbc.update("""
                insert into audit_event (id, organization_id, actor_id, entity_type, entity_id, action, details, occurred_at)
                values (?, ?, null, 'TICKET', ?, ?, ?::jsonb, ?)""",
            UUID.randomUUID(), alert.organizationId(), alert.ticketId(), "SLA_" + alert.level().name(),
            json.writeValueAsString(details), Timestamp.from(alert.occurredAt()));
    }

    /** Historial de un ticket, solo para el equipo: incluye la existencia de notas internas. */
    @Transactional(readOnly = true)
    public List<Entry> ticketHistory(UUID organizationId, AppUser actor, UUID ticketId) {
        access.require(organizationId, actor, Role.ADMIN, Role.TECHNICIAN);
        record Row(UUID id, String action, UUID actorId, String details, Instant occurredAt) {
        }
        List<Row> rows = jdbc.query("""
                select id, action, actor_id, details::text, occurred_at from audit_event
                where organization_id = ? and entity_type = 'TICKET' and entity_id = ?
                order by occurred_at, id""",
            (rs, i) -> new Row(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class),
                rs.getString(4), rs.getTimestamp(5).toInstant()),
            organizationId, ticketId);
        Map<UUID, AppUser> people = users.findAllById(rows.stream().map(Row::actorId).filter(Objects::nonNull).toList());
        return rows.stream()
            .map(r -> new Entry(r.id(), r.action(),
                r.actorId() == null || !people.containsKey(r.actorId()) ? null
                    : new Actor(r.actorId(), people.get(r.actorId()).getDisplayName()),
                json.readValue(r.details(), DETAILS), r.occurredAt()))
            .toList();
    }
}
