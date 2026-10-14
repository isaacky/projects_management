package com.pmplatform.platform.persistence;

import com.pmplatform.platform.tenant.TenantContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Tells every connection taken from the pool which tenant it works for, by setting the PostgreSQL
 * setting {@code app.tenant_id} (blank when there is no tenant). The row-level security policies
 * (see {@code afterMigrate__row_level_security.sql}) only show rows of that tenant, so a query that
 * forgets its {@code tenant_id} condition still cannot read or change another organisation's data.
 *
 * <p>The setting is made on every checkout, so a pooled connection never keeps the previous
 * borrower's tenant. Policies only bind roles that do not own the tables: production connects as
 * the application role ({@code pmp_app}); local development, connecting as the owner, is not
 * restricted.
 */
public class TenantConnectionDataSource extends DelegatingDataSource {

    static final String SET_TENANT = "select set_config('app.tenant_id', ?, false)";

    public TenantConnectionDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return withTenant(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return withTenant(super.getConnection(username, password));
    }

    /**
     * Code that looks behind the wrapper for the real pool (Spring Boot's DataSourceBuilder, pool
     * metrics) gets the pool itself; only {@link #getConnection} adds the tenant setting.
     */
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this) && !iface.isInstance(getTargetDataSource())) {
            return iface.cast(this);
        }
        DataSource target = getTargetDataSource();
        return iface.isInstance(target) ? iface.cast(target) : target.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        DataSource target = getTargetDataSource();
        return iface.isInstance(this) || iface.isInstance(target) || target.isWrapperFor(iface);
    }

    private static Connection withTenant(Connection connection) throws SQLException {
        String tenant = TenantContext.current().map(UUID::toString).orElse("");
        try (PreparedStatement ps = connection.prepareStatement(SET_TENANT)) {
            ps.setString(1, tenant);
            ps.execute();
        } catch (SQLException | RuntimeException e) {
            connection.close();
            throw e;
        }
        return connection;
    }
}
