package com.pmplatform.closeout;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Transaction-scoped PostgreSQL advisory locks for closeout work that spans rows other
 * contexts own (a contract's defects versus its retention release) or replaces a whole list.
 */
@Component
public class CloseoutLocks {

    private static final long CONTRACT = 0x434F4E54L; // "CONT"
    private static final long TEMPLATE = 0x54504C54L; // "TPLT"

    private final JdbcTemplate jdbc;

    public CloseoutLocks(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Held while defects of a contract are counted or changed, so retention release and defects serialise. */
    public void contract(UUID contractId) {
        if (contractId != null) {
            take(CONTRACT, contractId);
        }
    }

    /** Held while a tenant's standard checklist is replaced. */
    public void template(UUID tenantId) {
        take(TEMPLATE, tenantId);
    }

    private void take(long namespace, UUID id) {
        long key = (id.getMostSignificantBits() ^ id.getLeastSignificantBits()) * 31 + namespace;
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?)", key);
    }
}
