package com.pmplatform.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.pmplatform.platform.audit.AuditService;
import com.pmplatform.platform.tenant.TenantBootstrap;
import com.pmplatform.platform.tenant.TenantContext;
import com.pmplatform.platform.tenant.TenantDirectory;
import com.pmplatform.platform.user.CurrentUserService;
import com.pmplatform.platform.user.User;
import com.pmplatform.platform.user.UserContext;
import com.pmplatform.platform.user.UserRepository;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Pilot readiness: row-level security keeps tenants apart for the application role, a first sign-in
 * links to the person an administrator added, administrators manage people and roles, and the
 * start-up bootstrap creates an organisation with its first administrator.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pmp.storage.type=memory")
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class PilotReadinessIntegrationTest {

    private static final UUID TENANT = UUID.fromString("01920000-0000-7000-8000-000000000001");
    private static final String ADMIN = "01920000-0000-7000-8000-000000000101"; // PMO lead + SYSTEM_ADMIN
    private static final String REQUESTER = "01920000-0000-7000-8000-000000000102";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST =
            new ParameterizedTypeReference<>() { };

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Value("${local.server.port}")
    int port;

    @Autowired
    DataSource dataSource;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    UserRepository users;
    @Autowired
    AuditService audit;
    @Autowired
    Clock clock;
    @Autowired
    CurrentUserService currentUser;
    @Autowired
    TenantDirectory tenants;

    private RestClient as(String userId) {
        return RestClient.builder().baseUrl("http://localhost:" + port + "/api/v1")
                .defaultHeader("X-Dev-User-ID", userId)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { })
                .build();
    }

    private static long count(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    void applicationRoleSeesOnlyItsTenant() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            try {
                st.execute("set role pmp_app");
                st.execute("select set_config('app.tenant_id', '', false)");
                assertThat(count(st, "select count(*) from portfolio.projects")).isZero();
                st.execute("select set_config('app.tenant_id', '" + TENANT + "', false)");
                assertThat(count(st, "select count(*) from portfolio.projects")).isPositive();
                st.execute("select set_config('app.tenant_id', '" + UUID.randomUUID() + "', false)");
                assertThat(count(st, "select count(*) from portfolio.projects")).isZero();
                assertThat(count(st, "select count(*) from platform.users")).isZero();
                assertThat(count(st, "select count(*) from platform.user_roles")).isZero(); // child table
                assertThat(count(st, "select count(*) from procurement.evaluation_template_criteria")).isZero();
            } finally {
                st.execute("reset role");
            }
        }
        assertThat(count(jdbc, "select count(*) from pg_policies where policyname = 'tenant_isolation'")).isGreaterThan(50);
        assertThat(count(jdbc, "select count(*) from pg_class c join pg_namespace n on n.oid = c.relnamespace "
                + "where c.relkind = 'r' and not c.relrowsecurity and n.nspname in ('platform', 'portfolio', "
                + "'procurement', 'contracts', 'planning', 'execution', 'closeout', 'benefits') "
                + "and c.relname not in ('tenants', 'flyway_schema_history')")).isZero();
    }

    private static long count(JdbcTemplate jdbc, String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    @Test
    void administratorsManagePeopleAndRoles() {
        String email = "new.engineer." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        ResponseEntity<Map<String, Object>> created = as(ADMIN).post().uri("/admin/users")
                .body(Map.of("email", " " + email.toUpperCase() + " ", "displayName", "New Engineer",
                        "roles", List.of("PROJECT_MANAGER")))
                .retrieve().toEntity(MAP);
        assertThat(created.getStatusCode().value()).as(String.valueOf(created.getBody())).isEqualTo(201);
        assertThat(created.getBody()).containsEntry("email", email).containsEntry("linked", false);
        String id = (String) created.getBody().get("id");

        // Same email again (any case): refused.
        assertThat(as(ADMIN).post().uri("/admin/users")
                .body(Map.of("email", email, "displayName", "Twin", "roles", List.of()))
                .retrieve().toEntity(MAP).getStatusCode().value()).isEqualTo(409);
        // Bad role code: refused.
        assertThat(as(ADMIN).post().uri("/admin/users")
                .body(Map.of("email", "x" + email, "displayName", "X", "roles", List.of("pmo lead")))
                .retrieve().toEntity(MAP).getStatusCode().value()).isEqualTo(400);

        ResponseEntity<Map<String, Object>> updated = as(ADMIN).put().uri("/admin/users/" + id)
                .body(Map.of("email", email, "displayName", "New Engineer", "roles",
                        List.of("PROJECT_MANAGER", "CONTRACT_MANAGER"), "active", true))
                .retrieve().toEntity(MAP);
        assertThat(updated.getStatusCode().value()).isEqualTo(200);
        assertThat(updated.getBody().get("roles")).isEqualTo(List.of("CONTRACT_MANAGER", "PROJECT_MANAGER"));

        // Administrators cannot lock themselves out.
        assertThat(as(ADMIN).put().uri("/admin/users/" + ADMIN)
                .body(Map.of("email", "pmo.lead@example.com", "displayName", "PMO Lead (sample)",
                        "roles", List.of("PMO_LEAD"), "active", true))
                .retrieve().toEntity(MAP).getStatusCode().value()).isEqualTo(409);
        assertThat(as(ADMIN).put().uri("/admin/users/" + ADMIN)
                .body(Map.of("email", "pmo.lead@example.com", "displayName", "PMO Lead (sample)",
                        "roles", List.of("PMO_LEAD", "SYSTEM_ADMIN"), "active", false))
                .retrieve().toEntity(MAP).getStatusCode().value()).isEqualTo(409);
        // ... nor give themselves approval rights.
        assertThat(as(ADMIN).put().uri("/admin/users/" + ADMIN)
                .body(Map.of("email", "pmo.lead@example.com", "displayName", "PMO Lead (sample)",
                        "roles", List.of("PMO_LEAD", "SYSTEM_ADMIN", "ACCOUNTING_OFFICER"), "active", true))
                .retrieve().toEntity(MAP).getStatusCode().value()).isEqualTo(409);

        List<Map<String, Object>> roles = as(ADMIN).get().uri("/admin/roles").retrieve().toEntity(LIST).getBody();
        assertThat(roles).extracting(r -> r.get("code")).contains("SYSTEM_ADMIN", "PMO_LEAD", "HEAD_OF_DEPARTMENT");

        // Not an administrator.
        assertThat(as(REQUESTER).get().uri("/admin/users").retrieve().toEntity(LIST).getStatusCode().value())
                .isEqualTo(403);
    }

    @Test
    void firstSignInLinksByVerifiedEmail() {
        String email = "first.signin." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        assertThat(as(ADMIN).post().uri("/admin/users")
                .body(Map.of("email", email, "displayName", "First Sign-in", "roles", List.of("FINANCE_OFFICER")))
                .retrieve().toEntity(MAP).getStatusCode().value()).isEqualTo(201);
        String subject = "kc-" + UUID.randomUUID();
        TenantContext.set(TENANT);
        try {
            UserContext.set(subject, null, null); // email not verified: no link
            assertThat(currentUser.current()).isEmpty();

            UserContext.set(subject, email.toUpperCase(), null);
            Optional<User> me = currentUser.current();
            assertThat(me).isPresent();
            assertThat(me.get().getEmail()).isEqualTo(email);
            assertThat(me.get().getExternalSubject()).isEqualTo(subject);

            UserContext.set(subject, null, null); // later sign-ins match on the subject alone
            assertThat(currentUser.current()).isPresent();

            UserContext.set("kc-someone-else", email, null); // already linked to another account
            assertThat(currentUser.current()).isEmpty();
        } finally {
            TenantContext.clear();
            UserContext.clear();
        }
    }

    @Test
    void bootstrapCreatesTheOrganisationAndItsAdministrator() {
        UUID org = UUID.randomUUID();
        TenantBootstrap bootstrap = new TenantBootstrap(jdbc, tx, users, audit, clock, org.toString(),
                "Test Utility Ltd", "", "Admin@Test-Utility.example", "Test Administrator");
        bootstrap.run(null);
        bootstrap.run(null); // a second start changes nothing

        assertThat(count(jdbc, "select count(*) from platform.tenants where id = '" + org + "' and slug = 'test-utility-ltd'"))
                .isEqualTo(1);
        assertThat(count(jdbc, "select count(*) from procurement.methods where tenant_id = '" + org + "'")).isEqualTo(5);
        assertThat(count(jdbc, "select count(*) from procurement.evaluation_template_criteria c join "
                + "procurement.evaluation_templates t on t.id = c.template_id where t.tenant_id = '" + org + "'"))
                .isEqualTo(17);
        List<User> admins = users.findActiveWithRole(org, "SYSTEM_ADMIN");
        assertThat(admins).hasSize(1);
        assertThat(admins.get(0).getEmail()).isEqualTo("admin@test-utility.example");
        assertThat(tenants.activeTenantIds()).contains(org, TENANT);
    }
}
