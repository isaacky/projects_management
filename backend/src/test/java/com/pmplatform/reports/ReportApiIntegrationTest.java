package com.pmplatform.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Every report in the catalogue runs against the development sample data, returns typed columns,
 * and exports to an Excel workbook; bad requests are refused.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pmp.storage.type=memory")
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class ReportApiIntegrationTest {

    private static final String PMO = "01920000-0000-7000-8000-000000000101";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST =
            new ParameterizedTypeReference<>() { };

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Value("${local.server.port}")
    int port;

    private RestClient client() {
        return RestClient.builder().baseUrl("http://localhost:" + port + "/api/v1")
                .defaultHeader("X-Dev-User-ID", PMO)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { })
                .build();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> m, String key) {
        return (List<Map<String, Object>>) m.get(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> m, String key) {
        return (Map<String, Object>) m.get(key);
    }

    @Test
    void everyReportRunsAndExports() {
        List<Map<String, Object>> catalogue = client().get().uri("/reports").retrieve().toEntity(LIST).getBody();
        assertThat(catalogue).extracting(r -> r.get("id")).contains("portfolio-status", "risk-register",
                "procurement-plan", "contract-register", "payments", "supplier-performance", "benefits");

        for (Map<String, Object> info : catalogue) {
            String id = (String) info.get("id");
            ResponseEntity<Map<String, Object>> res = client().get().uri("/reports/" + id).retrieve().toEntity(MAP);
            assertThat(res.getStatusCode().value()).as(id + ": " + res.getBody()).isEqualTo(200);
            assertThat(list(res.getBody(), "columns")).as(id).isNotEmpty();
            assertThat(res.getBody()).containsEntry("id", id);

            ResponseEntity<byte[]> file = client().get().uri("/reports/" + id + "/xlsx").retrieve()
                    .toEntity(byte[].class);
            assertThat(file.getStatusCode().value()).as(id).isEqualTo(200);
            assertThat(file.getBody()).isNotNull();
            assertThat(file.getBody().length).as(id).isGreaterThan(500);
            assertThat(new String(file.getBody(), 0, 2)).isEqualTo("PK"); // a zip, as .xlsx files are
        }

        // The sample 2026/27 plan: seven lines, and its departments offered as a filter.
        Map<String, Object> plan = client().get().uri("/reports/procurement-plan").retrieve().toEntity(MAP).getBody();
        assertThat(list(plan, "rows")).hasSize(7);
        assertThat(((Number) map(plan, "totals").get("estimate")).longValue()).isEqualTo(94_530_000_000L);
        List<Map<String, Object>> filters = client().get().uri("/reports/procurement-plan/filters").retrieve()
                .toEntity(LIST).getBody();
        Map<String, Object> departments = filters.stream().filter(f -> "departmentId".equals(f.get("key")))
                .findFirst().orElseThrow();
        assertThat(list(departments, "options")).hasSize(4); // "All departments" + three

        // The sample benefits (active by default).
        Map<String, Object> benefits = client().get().uri("/reports/benefits").retrieve().toEntity(MAP).getBody();
        assertThat(list(benefits, "rows")).hasSize(3);

        // A blank filter means "all"; leaving it out gives the report's default.
        Map<String, Object> allRisks = client().get().uri("/reports/risk-register?status=").retrieve().toEntity(MAP)
                .getBody();
        assertThat((String) allRisks.get("subtitle")).startsWith("Open and closed");
        Map<String, Object> openRisks = client().get().uri("/reports/risk-register").retrieve().toEntity(MAP).getBody();
        assertThat((String) openRisks.get("subtitle")).startsWith("Open risks");

        // Refusals.
        assertThat(client().get().uri("/reports/benefits?status=NOPE").retrieve().toEntity(MAP).getStatusCode().value())
                .isEqualTo(409);
        assertThat(client().get().uri("/reports/nonsense").retrieve().toEntity(MAP).getStatusCode().value())
                .isEqualTo(404);
        assertThat(client().get().uri("/reports/payments?from=2026-06-01&to=2026-01-01").retrieve().toEntity(MAP)
                .getStatusCode().value()).isEqualTo(409);
    }
}
