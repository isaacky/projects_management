package com.pmplatform.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * End-to-end through HTTP, Flyway and a real PostgreSQL 17. Skipped automatically
 * when Docker is not available (it runs in CI).
 */
// No dev fallback tenant here, so the "requires a tenant" check is meaningful.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pmp.tenant.dev-default-id=")
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class ProjectApiIntegrationTest {

    private static final String TENANT = "01920000-0000-7000-8000-000000000001";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Value("${local.server.port}")
    int port;

    RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port + "/api/v1/projects")
                .defaultHeader("X-Tenant-ID", TENANT)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { })
                .build();
    }

    @Test
    void listsSeededProjects() {
        List<Map<String, Object>> projects = client.get().retrieve()
                .body(new ParameterizedTypeReference<>() { });
        assertThat(projects).extracting(p -> p.get("code"))
                .contains("NET-2026-001", "SUB-2026-004");
    }

    @Test
    void createsAdvancesAndAuditsAProject() {
        ResponseEntity<Map<String, Object>> created = client.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("code", "it-001", "name", "Integration test project",
                        "budgetMinor", 500_000_00L))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() { });
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        Map<String, Object> body = created.getBody();
        assertThat(body).containsEntry("code", "IT-001")
                .containsEntry("phase", "INITIATION")
                .containsEntry("currency", "KES");
        String id = (String) body.get("id");

        Map<String, Object> advanced = client.post().uri("/{id}/advance", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("decisionNote", "Business case approved by the board"))
                .retrieve()
                .body(new ParameterizedTypeReference<>() { });
        assertThat(advanced).containsEntry("phase", "PROCUREMENT");

        List<Map<String, Object>> history = client.get().uri("/{id}/history", id).retrieve()
                .body(new ParameterizedTypeReference<>() { });
        assertThat(history).extracting(h -> h.get("action"))
                .containsExactly("project.created", "project.gate.passed");
    }

    @Test
    void rejectsDuplicateCode() {
        ResponseEntity<String> dup = client.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("code", "NET-2026-001", "name", "Duplicate"))
                .retrieve()
                .toEntity(String.class);
        assertThat(dup.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void requiresATenant() {
        ResponseEntity<String> res = RestClient.create().get()
                .uri("http://localhost:" + port + "/api/v1/projects")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, r) -> { })
                .toEntity(String.class);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
    }
}
