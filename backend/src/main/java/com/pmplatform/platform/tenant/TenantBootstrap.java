package com.pmplatform.platform.tenant;

import com.pmplatform.platform.audit.AuditService;
import com.pmplatform.platform.id.UuidV7;
import com.pmplatform.platform.json.JsonText;
import com.pmplatform.platform.user.Roles;
import com.pmplatform.platform.user.User;
import com.pmplatform.platform.user.UserRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * First start on a server. With {@code pmp.bootstrap.tenant-id} set, makes sure the organisation
 * exists (loading starter procurement settings when it is new) and, with {@code admin-email} set,
 * that it has a system administrator: while nobody active holds {@code SYSTEM_ADMIN}, the person with
 * that email gets it (and is added if missing; someone deactivated is left alone, with a warning). They sign in with that email through
 * Keycloak and add everyone else on the Users and roles page. Safe to leave configured: once both
 * exist it changes nothing.
 */
@Component
public class TenantBootstrap implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(TenantBootstrap.class);
    static final String STARTER_SETTINGS = "bootstrap/starter-settings.sql";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final UserRepository users;
    private final AuditService audit;
    private final Clock clock;
    private final String tenantId;
    private final String tenantName;
    private final String tenantSlug;
    private final String adminEmail;
    private final String adminName;

    public TenantBootstrap(JdbcTemplate jdbc, TransactionTemplate tx, UserRepository users, AuditService audit,
            Clock clock,
            @Value("${pmp.bootstrap.tenant-id:}") String tenantId,
            @Value("${pmp.bootstrap.tenant-name:}") String tenantName,
            @Value("${pmp.bootstrap.tenant-slug:}") String tenantSlug,
            @Value("${pmp.bootstrap.admin-email:}") String adminEmail,
            @Value("${pmp.bootstrap.admin-name:System administrator}") String adminName) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
        this.tenantId = tenantId == null ? "" : tenantId.trim();
        this.tenantName = tenantName == null ? "" : tenantName.trim();
        this.tenantSlug = tenantSlug == null ? "" : tenantSlug.trim();
        this.adminEmail = adminEmail == null ? "" : adminEmail.trim();
        this.adminName = adminName == null || adminName.isBlank() ? "System administrator" : adminName.trim();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (tenantId.isEmpty()) {
            return;
        }
        UUID tenant = UUID.fromString(tenantId); // fail start-up on a malformed id rather than run half set up
        TenantContext.callAs(tenant, () -> tx.execute(status -> {
            ensureTenant(tenant);
            if (!adminEmail.isEmpty()) {
                ensureAdmin(tenant);
            }
            return null;
        }));
    }

    private void ensureTenant(UUID tenant) {
        Integer exists = jdbc.queryForObject("select count(*) from platform.tenants where id = ?", Integer.class,
                tenant);
        if (exists != null && exists > 0) {
            return;
        }
        if (tenantName.isEmpty()) {
            throw new IllegalStateException("pmp.bootstrap.tenant-name (PMP_TENANT_NAME) is needed to create tenant "
                    + tenant);
        }
        String slug = tenantSlug.isEmpty() ? slugify(tenantName) : tenantSlug;
        jdbc.update("insert into platform.tenants (id, slug, name) values (?, ?, ?)", tenant, slug, tenantName);
        for (String statement : starterSettings(tenant).split(";\\s*\\n")) {
            if (!statement.replaceAll("(?m)^\\s*--.*$", "").isBlank()) {
                jdbc.execute(statement);
            }
        }
        LOG.info("Created tenant {} ({}) with starter procurement settings", tenantName, tenant);
    }

    private void ensureAdmin(UUID tenant) {
        boolean hasAdmin = users.findActiveWithRole(tenant, Roles.SYSTEM_ADMIN).stream().findAny().isPresent();
        if (hasAdmin) {
            return;
        }
        Optional<User> existing = users.findByTenantIdAndEmail(tenant, adminEmail);
        if (existing.isPresent() && !existing.get().isActive()) {
            // Deactivated on purpose: do not bring them back silently.
            LOG.warn("Tenant {} has no active system administrator, and {} (pmp.bootstrap.admin-email) is "
                    + "deactivated. Set PMP_ADMIN_EMAIL to someone else and restart.", tenant, adminEmail);
            return;
        }
        User admin = existing.orElseGet(() -> User.create(UuidV7.generate(), tenant, adminEmail, adminName,
                Set.of(), Instant.now(clock)));
        admin.addRole(Roles.SYSTEM_ADMIN);
        users.save(admin);
        audit.record(tenant, "user.bootstrap_admin", "User", admin.getId(),
                JsonText.object("email", admin.getEmail(), "created", existing.isEmpty()));
        LOG.info("{} is now a system administrator of tenant {}", admin.getEmail(), tenant);
    }

    static String slugify(String name) {
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? "tenant" : slug.substring(0, Math.min(slug.length(), 64));
    }

    private static String starterSettings(UUID tenant) {
        try (InputStream in = new ClassPathResource(STARTER_SETTINGS).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("{{TENANT_ID}}", tenant.toString());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + STARTER_SETTINGS, e);
        }
    }
}
