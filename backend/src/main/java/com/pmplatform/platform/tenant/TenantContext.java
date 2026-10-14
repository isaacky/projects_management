package com.pmplatform.platform.tenant;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Holds the tenant for the current request. Set by {@link TenantFilter}, cleared
 * when the request ends. Every repository query must be scoped to this tenant.
 *
 * <p>The database enforces the same boundary: each connection handed out by the pool is told this
 * tenant ({@code app.tenant_id}), and row-level security hides other tenants' rows. Background jobs
 * therefore work one tenant at a time through {@link #callAs}.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    public static Optional<UUID> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static UUID require() {
        return current().orElseThrow(MissingTenantException::new);
    }

    public static void clear() {
        CURRENT.remove();
    }

    /**
     * Runs {@code work} as the given tenant (for scheduled jobs and start-up tasks, which have no
     * request), restoring whatever was set before. Open the transaction inside {@code work}, so its
     * database connection is taken after the tenant is set.
     */
    public static <T> T callAs(UUID tenantId, Supplier<T> work) {
        UUID previous = CURRENT.get();
        CURRENT.set(tenantId);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
