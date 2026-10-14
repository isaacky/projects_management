package com.pmplatform.procurement.tender;

import java.time.Clock;
import java.time.Year;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** TND-2026-0001, TND-2026-0002, ... per tenant and year (East Africa Time). */
@Component
public class TenderNumbers {

    private static final ZoneId EAT = ZoneId.of("Africa/Nairobi");

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public TenderNumbers(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(UUID tenantId) {
        int year = Year.now(clock.withZone(EAT)).getValue();
        Integer n = jdbc.queryForObject("""
                INSERT INTO procurement.tender_counters (tenant_id, year, next_value)
                VALUES (?, ?, 1)
                ON CONFLICT (tenant_id, year)
                DO UPDATE SET next_value = procurement.tender_counters.next_value + 1
                RETURNING next_value
                """, Integer.class, tenantId, year);
        return "TND-%d-%04d".formatted(year, n);
    }
}
