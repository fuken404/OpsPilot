package dev.fuken404.opspilot.dashboard;

import dev.fuken404.opspilot.identity.AppUser;
import dev.fuken404.opspilot.organizations.Organizations;
import dev.fuken404.opspilot.organizations.Role;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Dashboards {

    private static final List<String> OPEN_STATUSES = List.of("NEW", "IN_PROGRESS", "WAITING_CUSTOMER", "RESOLVED");
    private static final List<String> PRIORITIES = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final String OPEN = "status not in ('CLOSED', 'CANCELLED')";
    private static final int TREND_DAYS = 14;
    private static final int COMPLIANCE_DAYS = 30;

    private final JdbcTemplate jdbc;
    private final Organizations organizations;
    private final ZoneId zone;
    private final Clock clock;

    Dashboards(JdbcTemplate jdbc, Organizations organizations, @Value("${opspilot.sla.zone}") String zone, Clock clock) {
        this.jdbc = jdbc;
        this.organizations = organizations;
        this.zone = ZoneId.of(zone);
        this.clock = clock;
    }

    public record SlaNow(long running, long atRisk, long breached, long paused) {
    }

    /** Porcentaje de compromisos cumplidos a tiempo entre los que terminaron en los últimos 30 días. */
    public record Compliance(Double response, long responseTotal, Double resolution, long resolutionTotal) {
    }

    public record Workload(UUID userId, String displayName, Role role, long open, long breached) {
    }

    public record Day(LocalDate date, long created, long resolved) {
    }

    public record Dashboard(Instant generatedAt, long open, long unassigned, Map<String, Long> byStatus,
                            Map<String, Long> byPriority, SlaNow sla, Compliance compliance, List<Workload> workload,
                            List<Day> trend, Double averageResolutionHours) {
    }

    @Transactional(readOnly = true)
    public Dashboard of(UUID organizationId, AppUser actor) {
        // members() exige ADMIN o TECHNICIAN y deja la organización activa para Row Level Security.
        List<Organizations.Member> staff = organizations.members(organizationId, actor).stream()
            .filter(m -> !m.pending() && m.role() != Role.CUSTOMER)
            .toList();
        Instant now = clock.instant();
        Timestamp nowTs = Timestamp.from(now);

        Map<String, Long> byStatus = counts(OPEN_STATUSES,
            "select status, count(*) from ticket where organization_id = ? and " + OPEN + " group by status",
            organizationId);
        Map<String, Long> byPriority = counts(PRIORITIES,
            "select priority, count(*) from ticket where organization_id = ? and " + OPEN + " group by priority",
            organizationId);
        long open = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long unassigned = jdbc.queryForObject(
            "select count(*) from ticket where organization_id = ? and assignee_id is null and " + OPEN,
            Long.class, organizationId);

        SlaNow sla = jdbc.queryForObject("""
                select count(distinct ticket_id) filter (where paused_since is null and warn_at > ?),
                       count(distinct ticket_id) filter (where paused_since is null and warn_at <= ? and due_at > ?),
                       count(distinct ticket_id) filter (where paused_since is null and due_at <= ?),
                       count(distinct ticket_id) filter (where paused_since is not null)
                from sla_clock
                where organization_id = ? and met_at is null and stopped_at is null""",
            (rs, i) -> new SlaNow(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)),
            nowTs, nowTs, nowTs, nowTs, organizationId);

        Compliance compliance = jdbc.queryForObject("""
                select count(*) filter (where kind = 'RESPONSE'),
                       count(*) filter (where kind = 'RESPONSE' and met_at <= due_at),
                       count(*) filter (where kind = 'RESOLUTION'),
                       count(*) filter (where kind = 'RESOLUTION' and met_at <= due_at)
                from sla_clock
                where organization_id = ? and met_at >= ?""",
            (rs, i) -> new Compliance(rate(rs.getLong(2), rs.getLong(1)), rs.getLong(1),
                rate(rs.getLong(4), rs.getLong(3)), rs.getLong(3)),
            organizationId, Timestamp.from(now.minus(COMPLIANCE_DAYS, ChronoUnit.DAYS)));

        Map<UUID, long[]> load = new HashMap<>();
        jdbc.query("""
                select t.assignee_id, count(distinct t.id),
                       count(distinct t.id) filter (where c.breached_at is not null or c.due_at <= ?)
                from ticket t
                left join sla_clock c on c.ticket_id = t.id and c.met_at is null and c.stopped_at is null
                                     and c.paused_since is null
                where t.organization_id = ? and t.assignee_id is not null
                  and t.status not in ('CLOSED', 'CANCELLED')
                group by t.assignee_id""",
            rs -> {
                load.put(rs.getObject(1, UUID.class), new long[] {rs.getLong(2), rs.getLong(3)});
            },
            nowTs, organizationId);
        List<Workload> workload = staff.stream()
            .map(m -> {
                long[] counts = load.getOrDefault(m.userId(), new long[2]);
                return new Workload(m.userId(), m.displayName(), m.role(), counts[0], counts[1]);
            })
            .sorted(Comparator.comparingLong(Workload::open).reversed().thenComparing(Workload::displayName))
            .toList();

        Double averageHours = jdbc.queryForObject("""
                select avg(extract(epoch from resolved_at - created_at)) / 3600 from ticket
                where organization_id = ? and resolved_at >= ?""",
            Double.class, organizationId, Timestamp.from(now.minus(COMPLIANCE_DAYS, ChronoUnit.DAYS)));

        return new Dashboard(now, open, unassigned, byStatus, byPriority, sla, compliance, workload,
            trend(organizationId, now), averageHours == null ? null : Math.round(averageHours * 10) / 10.0);
    }

    /** Creados y resueltos por día en la zona horaria de la operación, con los días vacíos en cero. */
    private List<Day> trend(UUID organizationId, Instant now) {
        LocalDate today = now.atZone(zone).toLocalDate();
        LocalDate first = today.minusDays(TREND_DAYS - 1);
        Timestamp since = Timestamp.from(first.atStartOfDay(zone).toInstant());
        Map<LocalDate, Long> created = perDay("created_at", organizationId, since);
        Map<LocalDate, Long> resolved = perDay("resolved_at", organizationId, since);
        List<Day> days = new ArrayList<>();
        for (LocalDate day = first; !day.isAfter(today); day = day.plusDays(1)) {
            days.add(new Day(day, created.getOrDefault(day, 0L), resolved.getOrDefault(day, 0L)));
        }
        return days;
    }

    private Map<LocalDate, Long> perDay(String column, UUID organizationId, Timestamp since) {
        Map<LocalDate, Long> result = new HashMap<>();
        // column es una de dos constantes de este archivo, nunca entrada del usuario.
        jdbc.query("select (" + column + " at time zone ?)::date, count(*) from ticket "
                + "where organization_id = ? and " + column + " >= ? group by 1",
            rs -> {
                result.put(rs.getObject(1, LocalDate.class), rs.getLong(2));
            },
            zone.getId(), organizationId, since);
        return result;
    }

    private Map<String, Long> counts(List<String> keys, String sql, UUID organizationId) {
        Map<String, Long> result = new LinkedHashMap<>();
        keys.forEach(key -> result.put(key, 0L));
        jdbc.query(sql, rs -> {
            result.put(rs.getString(1), rs.getLong(2));
        }, organizationId);
        return result;
    }

    private static Double rate(long met, long total) {
        return total == 0 ? null : Math.round(met * 1000.0 / total) / 10.0;
    }
}
