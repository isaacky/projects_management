package com.pmplatform.procurement.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
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

/** Procurement settings through HTTP against PostgreSQL 17 with the dev sample settings. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class ProcurementSettingsApiIntegrationTest {

    private static final String TENANT = "01920000-0000-7000-8000-000000000001";
    private static final String HEAD_OF_PROCUREMENT = "01920000-0000-7000-8000-000000000104";
    private static final String REQUESTER = "01920000-0000-7000-8000-000000000102";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Value("${local.server.port}")
    int port;

    RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port + "/api/v1/procurement/settings")
                .defaultHeader("X-Tenant-ID", TENANT)
                .defaultHeader("X-Dev-User-ID", HEAD_OF_PROCUREMENT)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { })
                .build();
    }

    @Test
    void onlyTheHeadOfProcurementChangesSettings() {
        RestClient requester = RestClient.builder()
                .baseUrl("http://localhost:" + port + "/api/v1/procurement/settings")
                .defaultHeader("X-Tenant-ID", TENANT)
                .defaultHeader("X-Dev-User-ID", REQUESTER)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { })
                .build();
        assertThat(requester.get().retrieve().toEntity(MAP).getStatusCode().value()).isEqualTo(200);
        assertThat(requester.delete().uri("/committee-rules/RFQ").retrieve().toBodilessEntity().getStatusCode().value())
                .isEqualTo(403);
        RestClient admin = RestClient.builder() // system administrators manage people, not thresholds
                .baseUrl("http://localhost:" + port + "/api/v1/procurement/settings")
                .defaultHeader("X-Tenant-ID", TENANT)
                .defaultHeader("X-Dev-User-ID", "01920000-0000-7000-8000-000000000101")
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { })
                .build();
        assertThat(admin.delete().uri("/committee-rules/RFQ").retrieve().toBodilessEntity().getStatusCode().value())
                .isEqualTo(403);
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsAllThreeSettingTables() {
        Map<String, Object> settings = client.get().retrieve().body(MAP);
        assertThat((List<Map<String, Object>>) settings.get("methods"))
                .extracting(m -> m.get("code")).contains("OPEN_TENDER", "RFQ", "DIRECT", "FRAMEWORK");
        assertThat((List<?>) settings.get("approvalThresholds")).isNotEmpty();
        assertThat((List<Map<String, Object>>) settings.get("committeeRules"))
                .extracting(r -> r.get("methodCode")).contains(null, "OPEN_TENDER");
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkAppliesTheRulesToAPurchase() {
        Map<String, Object> rfq = client.get()
                .uri("/check?stage=AWARD&method=RFQ&amountMinor=150000000").retrieve().body(MAP);
        assertThat(rfq.get("methodAllowed")).isEqualTo(true);
        assertThat((List<Map<String, Object>>) rfq.get("approvers"))
                .extracting(a -> a.get("approverRole")).containsExactly("HEAD_OF_PROCUREMENT");
        assertThat((Map<String, Object>) rfq.get("committeeRule")).containsEntry("methodCode", "RFQ");

        Map<String, Object> bigRfq = client.get()
                .uri("/check?stage=AWARD&method=RFQ&amountMinor=900000000").retrieve().body(MAP);
        assertThat(bigRfq.get("methodAllowed")).isEqualTo(false);
        assertThat((List<?>) bigRfq.get("warnings")).isNotEmpty();
    }

    @Test
    void rejectsOverlappingApprovalBands() {
        Map<String, Object> band = new HashMap<>();
        band.put("stage", "REQUISITION");
        band.put("minAmountMinor", 100_000_000L);
        band.put("maxAmountMinor", 200_000_000L);
        band.put("currency", "KES");
        band.put("stepOrder", 1);
        band.put("approverRole", "FINANCE_MANAGER");
        band.put("active", true);

        ResponseEntity<String> res = client.post().uri("/approval-thresholds")
                .contentType(MediaType.APPLICATION_JSON)
                .body(band).retrieve().toEntity(String.class);
        assertThat(res.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void createsUpdatesAndDeletesAMethodSpecificCommitteeRule() {
        Map<String, Object> method = new HashMap<>();
        method.put("name", "Specially permitted procurement");
        method.put("enabled", true);
        method.put("currency", "KES");
        method.put("minBidPeriodDays", 7);
        method.put("requiresPublicAdvert", false);
        method.put("requiresJustification", true);
        method.put("sortOrder", 60);
        assertThat(client.put().uri("/methods/SPECIAL").contentType(MediaType.APPLICATION_JSON)
                .body(method).retrieve().toEntity(String.class).getStatusCode().value()).isEqualTo(200);

        Map<String, Object> rule = new HashMap<>();
        rule.put("minMembers", 3);
        rule.put("maxMembers", 5);
        rule.put("quorum", 3);
        rule.put("twoEnvelope", true);
        rule.put("technicalPassMarkPct", 75);
        rule.put("requiresCoiDeclaration", true);
        rule.put("reportSignatories", "CHAIR_AND_SECRETARY");
        rule.put("awardApproverRole", "ACCOUNTING_OFFICER");
        Map<String, Object> saved = client.put().uri("/committee-rules/SPECIAL")
                .contentType(MediaType.APPLICATION_JSON).body(rule).retrieve().body(MAP);
        assertThat(saved).containsEntry("twoEnvelope", true).containsEntry("technicalPassMarkPct", 75);

        rule.put("quorum", 4);
        rule.put("minMembers", 3);
        ResponseEntity<String> badQuorum = client.put().uri("/committee-rules/SPECIAL")
                .contentType(MediaType.APPLICATION_JSON).body(rule).retrieve().toEntity(String.class);
        assertThat(badQuorum.getStatusCode().value()).isEqualTo(409);

        assertThat(client.delete().uri("/committee-rules/SPECIAL").retrieve().toBodilessEntity()
                .getStatusCode().value()).isEqualTo(204);
        assertThat(client.delete().uri("/committee-rules/DEFAULT").retrieve().toBodilessEntity()
                .getStatusCode().value()).isEqualTo(409);
    }
}
