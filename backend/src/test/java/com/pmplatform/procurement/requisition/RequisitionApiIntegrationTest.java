package com.pmplatform.procurement.requisition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
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
 * A requisition through the whole chain, acting as the sample users: requester raises
 * KES 6m (above the KES 5m sample band), so head of department then accounting officer approve.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class RequisitionApiIntegrationTest {

    private static final String REQUESTER = "01920000-0000-7000-8000-000000000102";
    private static final String HOD = "01920000-0000-7000-8000-000000000103";
    private static final String AO = "01920000-0000-7000-8000-000000000105";
    private static final String PROJECT = "01920000-0000-7000-8000-000000001003";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Value("${local.server.port}")
    int port;

    private RestClient as(String userId) {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port + "/api/v1")
                .defaultHeader("X-Dev-User-ID", userId)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { })
                .build();
    }

    private Map<String, Object> post(String user, String path, Object body) {
        return as(user).post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body == null ? Map.of() : body)
                .retrieve().body(MAP);
    }

    @Test
    @SuppressWarnings("unchecked")
    void requisitionGoesThroughTheTwoStepChain() {
        Map<String, Object> draft = post(REQUESTER, "/requisitions", Map.of(
                "projectId", PROJECT,
                "title", "Smart meters, batch 1",
                "lines", List.of(Map.of("description", "Smart meter", "quantity", 1000,
                        "unit", "each", "unitPriceMinor", 600_000L))));
        assertThat(draft).containsEntry("status", "DRAFT");
        assertThat((String) draft.get("number")).startsWith("REQ-");
        assertThat(draft.get("estimatedTotalMinor")).isEqualTo(600_000_000);
        String id = (String) draft.get("id");

        Map<String, Object> submitted = post(REQUESTER, "/requisitions/" + id + "/submit", null);
        assertThat(submitted).containsEntry("status", "SUBMITTED");
        assertThat((List<Map<String, Object>>) submitted.get("approvals"))
                .extracting(a -> a.get("approverRole"))
                .containsExactly("HEAD_OF_DEPARTMENT", "ACCOUNTING_OFFICER");

        // The head of department sees it in their queue and inbox.
        List<Map<String, Object>> queue = as(HOD).get().uri("/requisitions?scope=AWAITING_ME").retrieve()
                .body(new ParameterizedTypeReference<>() { });
        assertThat(queue).extracting(q -> q.get("id")).contains(id);
        Map<String, Object> inbox = as(HOD).get().uri("/notifications").retrieve().body(MAP);
        assertThat(((Number) inbox.get("unreadCount")).intValue()).isPositive();

        // Wrong person, and the requester approving their own: refused.
        assertThat(as(AO).post().uri("/requisitions/" + id + "/approve").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of()).retrieve().toEntity(String.class).getStatusCode().value()).isEqualTo(403);

        assertThat(post(HOD, "/requisitions/" + id + "/approve", Map.of("comment", "Within budget")))
                .containsEntry("status", "SUBMITTED");
        assertThat(post(AO, "/requisitions/" + id + "/approve", null)).containsEntry("status", "APPROVED");

        Map<String, Object> requesterInbox = as(REQUESTER).get().uri("/notifications").retrieve().body(MAP);
        assertThat((List<Map<String, Object>>) requesterInbox.get("items"))
                .extracting(n -> n.get("type")).contains("requisition.approved");

        List<Map<String, Object>> history = as(REQUESTER).get().uri("/requisitions/" + id + "/history").retrieve()
                .body(new ParameterizedTypeReference<>() { });
        assertThat(history).extracting(h -> h.get("action")).containsExactly(
                "requisition.created", "requisition.submitted", "requisition.approved_step",
                "requisition.approved_step");
    }

    @Test
    void rejectionNeedsAReason() {
        Map<String, Object> draft = post(REQUESTER, "/requisitions", Map.of(
                "projectId", PROJECT, "title", "Tools",
                "lines", List.of(Map.of("description", "Toolkit", "quantity", 2, "unit", "set",
                        "unitPriceMinor", 1_500_000L))));
        String id = (String) draft.get("id");
        post(REQUESTER, "/requisitions/" + id + "/submit", null);

        ResponseEntity<String> noReason = as(HOD).post().uri("/requisitions/" + id + "/reject")
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("comment", "")).retrieve().toEntity(String.class);
        assertThat(noReason.getStatusCode().value()).isEqualTo(409);

        assertThat(post(HOD, "/requisitions/" + id + "/reject", Map.of("comment", "Use framework stock")))
                .containsEntry("status", "REJECTED");
    }
}
