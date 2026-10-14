package com.pmplatform.procurement.plan;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

/**
 * What has happened against the plan: requisitions drawing on each line, tenders running and
 * awards made, and requisitions raised outside the plan. Read straight from the procurement
 * tables (all in this bounded context).
 */
@Component
public class PlanActuals {

    /** Per line. committed = requisitions submitted or approved. */
    public record LineActual(long committedMinor, int requisitions, int activeTenders, long awardedMinor) {
        static final LineActual NONE = new LineActual(0, 0, 0, 0);
    }

    public record Unplanned(UUID requisitionId, String number, String title, long amountMinor, String reason,
            String status, Instant submittedAt) {
    }

    private static final ZoneId EAT = ZoneId.of("Africa/Nairobi");

    private final JdbcTemplate jdbc;

    public PlanActuals(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<UUID, LineActual> forPlans(UUID tenantId, List<UUID> planIds) {
        Map<UUID, LineActual> out = new HashMap<>();
        if (planIds.isEmpty()) {
            return out;
        }
        String in = String.join(", ", java.util.Collections.nCopies(planIds.size(), "?"));
        Object[] args = new Object[planIds.size() + 1];
        args[0] = tenantId;
        for (int i = 0; i < planIds.size(); i++) {
            args[i + 1] = planIds.get(i);
        }
        jdbc.query("""
                SELECT r.plan_line_id,
                       COALESCE(SUM(r.estimated_total_minor) FILTER (WHERE r.status IN ('SUBMITTED', 'APPROVED')), 0),
                       COUNT(*) FILTER (WHERE r.status IN ('SUBMITTED', 'APPROVED')),
                       (SELECT COUNT(*) FROM procurement.tenders t JOIN procurement.requisitions r2
                               ON r2.id = t.requisition_id
                         WHERE r2.plan_line_id = r.plan_line_id AND t.awarded_amount_minor IS NULL
                           AND t.status NOT IN ('CANCELLED', 'TERMINATED')),
                       (SELECT COALESCE(SUM(t.awarded_amount_minor), 0) FROM procurement.tenders t
                          JOIN procurement.requisitions r2 ON r2.id = t.requisition_id
                         WHERE r2.plan_line_id = r.plan_line_id AND t.awarded_amount_minor IS NOT NULL
                           AND t.status NOT IN ('CANCELLED', 'TERMINATED'))
                  FROM procurement.requisitions r
                  JOIN procurement.plan_lines l ON l.id = r.plan_line_id
                 WHERE r.tenant_id = ? AND l.plan_id IN (%s)
                 GROUP BY r.plan_line_id
                """.formatted(in), (RowCallbackHandler) rs -> {
            out.put(rs.getObject(1, UUID.class),
                    new LineActual(rs.getLong(2), rs.getInt(3), rs.getInt(4), rs.getLong(5)));
        }, args);
        return out;
    }

    /** Requisitions drawing on the line (submitted or approved), leaving one out. */
    public long committed(UUID lineId, UUID exceptRequisitionId) {
        Long v = jdbc.queryForObject("""
                SELECT COALESCE(SUM(estimated_total_minor), 0) FROM procurement.requisitions
                 WHERE plan_line_id = ? AND status IN ('SUBMITTED', 'APPROVED') AND id <> ?
                """, Long.class, lineId, exceptRequisitionId);
        return v == null ? 0 : v;
    }

    public boolean anyRequisition(UUID lineId) {
        Boolean b = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM procurement.requisitions WHERE plan_line_id = ?)", Boolean.class, lineId);
        return Boolean.TRUE.equals(b);
    }

    /** Unplanned requisitions submitted during the financial year that are in approval or approved. */
    public List<Unplanned> unplanned(UUID tenantId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT id, number, title, estimated_total_minor, unplanned_reason, status, submitted_at
                  FROM procurement.requisitions
                 WHERE tenant_id = ? AND plan_status = 'UNPLANNED'
                   AND status IN ('SUBMITTED', 'APPROVED')
                   AND submitted_at >= ? AND submitted_at < ?
                 ORDER BY submitted_at DESC
                 LIMIT 500
                """, (rs, i) -> new Unplanned(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getLong(4), rs.getString(5), rs.getString(6),
                        rs.getTimestamp(7) == null ? null : rs.getTimestamp(7).toInstant()),
                tenantId, from.atStartOfDay(EAT).toOffsetDateTime(), to.plusDays(1).atStartOfDay(EAT).toOffsetDateTime());
    }
}
