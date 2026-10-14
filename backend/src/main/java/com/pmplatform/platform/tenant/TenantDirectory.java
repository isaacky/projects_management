package com.pmplatform.platform.tenant;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** The organisations using the platform, for background jobs that visit each one in turn. */
@Component
public class TenantDirectory {

    private final JdbcTemplate jdbc;

    public TenantDirectory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Active tenants, oldest first. (The tenants table itself is not tenant-scoped.) */
    public List<UUID> activeTenantIds() {
        return jdbc.queryForList("select id from platform.tenants where status = 'ACTIVE' order by created_at, id",
                UUID.class);
    }
}
