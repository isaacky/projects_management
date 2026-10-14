package com.pmplatform.procurement.tender;

import static org.assertj.core.api.Assertions.assertThat;

import com.pmplatform.testsupport.MutableClock;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The sealed-bid flow: approved requisition, tender, publication, a sealed bid, refusal of
 * early opening and late bids, the technical opening with quorum, and who may read opened bids.
 * Evaluation, the financial opening and award are covered by EvaluationApiIntegrationTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pmp.storage.type=memory")
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class TenderApiIntegrationTest {

    private static final String PMO = "01920000-0000-7000-8000-000000000101";
    private static final String REQUESTER = "01920000-0000-7000-8000-000000000102";
    private static final String HOD = "01920000-0000-7000-8000-000000000103";
    private static final String HOP = "01920000-0000-7000-8000-000000000104";
    private static final String AO = "01920000-0000-7000-8000-000000000105";
    private static final String PO = "01920000-0000-7000-8000-000000000106";
    private static final String PROJECT = "01920000-0000-7000-8000-000000001003";
    private static final String SUPPLIER = "01920000-0000-7000-8000-000000005001";
    private static final String DEBARRED = "01920000-0000-7000-8000-000000005005";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Autowired
    MutableClock clock;

    @Value("${local.server.port}")
    int port;

    private RestClient as(String userId) {
        return RestClient.builder().baseUrl("http://localhost:" + port + "/api/v1")
                .defaultHeader("X-Dev-User-ID", userId)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { })
                .build();
    }

    private ResponseEntity<Map<String, Object>> post(String user, String path, Object body) {
        return as(user).post().uri(path).contentType(MediaType.APPLICATION_JSON)
                .body(body == null ? Map.of() : body).retrieve().toEntity(MAP);
    }

    private ResponseEntity<Map<String, Object>> multipart(String user, String path, MultiValueMap<String, Object> parts) {
        return as(user).post().uri(path).contentType(MediaType.MULTIPART_FORM_DATA).body(parts).retrieve().toEntity(MAP);
    }

    private static ByteArrayResource file(String name, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return name;
            }
        };
    }

    private String approvedRequisition() {
        Map<String, Object> req = post(REQUESTER, "/requisitions", Map.of("projectId", PROJECT,
                "title", "Distribution transformers",
                "lines", List.of(Map.of("description", "Transformer 100 kVA", "quantity", 2, "unit", "each",
                        "unitPriceMinor", 50_000_000L)))).getBody();
        String id = (String) req.get("id");
        post(REQUESTER, "/requisitions/" + id + "/submit", null);
        assertThat(post(HOD, "/requisitions/" + id + "/approve", null).getBody()).containsEntry("status", "APPROVED");
        return id;
    }

    @Test
    @SuppressWarnings("unchecked")
    void sealedBidLifecycle() {
        String requisitionId = approvedRequisition();

        // Only procurement roles may create tenders.
        assertThat(post(REQUESTER, "/tenders", Map.of("requisitionId", requisitionId, "methodCode", "OPEN_TENDER"))
                .getStatusCode().value()).isEqualTo(403);

        Map<String, Object> tender = post(PO, "/tenders",
                Map.of("requisitionId", requisitionId, "methodCode", "OPEN_TENDER")).getBody();
        assertThat(tender).containsEntry("status", "DRAFT").containsEntry("twoEnvelope", true)
                .containsEntry("openingQuorum", 4);
        String id = (String) tender.get("id");
        String base = "/tenders/" + id;

        // A second tender for the same requisition is refused.
        assertThat(post(PO, "/tenders", Map.of("requisitionId", requisitionId, "methodCode", "OPEN_TENDER"))
                .getStatusCode().value()).isEqualTo(409);

        MultiValueMap<String, Object> doc = new LinkedMultiValueMap<>();
        doc.add("file", file("tender-document.pdf", "%PDF-1.7 tender document"));
        assertThat(multipart(PO, base + "/documents", doc).getStatusCode().value()).isEqualTo(200);

        Instant closing = Instant.now(clock).plus(Duration.ofDays(21)).plus(Duration.ofHours(1));
        as(PO).put().uri(base).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", "Supply of distribution transformers", "closingAt", closing.toString()))
                .retrieve().toBodilessEntity();
        assertThat(post(PO, base + "/publish", null).getBody()).containsEntry("status", "PUBLISHED");

        // A debarred supplier cannot bid; an active one can.
        MultiValueMap<String, Object> badBid = new LinkedMultiValueMap<>();
        badBid.add("supplierId", DEBARRED);
        badBid.add("technical", file("tech.pdf", "x"));
        assertThat(multipart(PO, base + "/bids", badBid).getStatusCode().value()).isEqualTo(409);

        MultiValueMap<String, Object> bid = new LinkedMultiValueMap<>();
        bid.add("supplierId", SUPPLIER);
        bid.add("technical", file("technical-proposal.pdf", "TECHNICAL CONTENT"));
        bid.add("financial", file("price-schedule.pdf", "PRICE: 95,000,000"));
        Map<String, Object> afterBid = multipart(PO, base + "/bids", bid).getBody();
        Map<String, Object> theBid = ((List<Map<String, Object>>) afterBid.get("bids")).get(0);
        assertThat((String) theBid.get("receiptNo")).endsWith("-B001");
        assertThat((String) theBid.get("receiptHash")).hasSize(64);
        List<Map<String, Object>> files = (List<Map<String, Object>>) theBid.get("files");
        assertThat(files).allMatch(f -> Boolean.TRUE.equals(f.get("sealed")) && f.get("fileName") == null);
        String bidId = (String) theBid.get("id");
        String techFile = (String) files.stream().filter(f -> "TECHNICAL".equals(f.get("envelope"))).findFirst()
                .orElseThrow().get("id");
        String finFile = (String) files.stream().filter(f -> "FINANCIAL".equals(f.get("envelope"))).findFirst()
                .orElseThrow().get("id");

        // Sealed: no download, no opening before closing.
        assertThat(as(PO).get().uri(base + "/bids/" + bidId + "/files/" + techFile).retrieve()
                .toEntity(byte[].class).getStatusCode().value()).isEqualTo(409);
        Map<String, Object> opening = Map.of("envelope", "TECHNICAL", "attendeeIds", List.of(PO, HOP, HOD, AO));
        assertThat(post(PO, base + "/openings", opening).getStatusCode().value()).isEqualTo(409);

        // Past the closing time: late bids refused, opening allowed with quorum.
        clock.advance(Duration.ofDays(22));
        MultiValueMap<String, Object> late = new LinkedMultiValueMap<>();
        late.add("supplierId", "01920000-0000-7000-8000-000000005002");
        late.add("technical", file("late.pdf", "late"));
        assertThat(multipart(PO, base + "/bids", late).getStatusCode().value()).isEqualTo(409);

        assertThat(post(PO, base + "/openings",
                Map.of("envelope", "TECHNICAL", "attendeeIds", List.of(PO, HOP, HOD))).getStatusCode().value())
                .isEqualTo(409); // 3 < quorum of 4
        assertThat(post(PO, base + "/openings", opening).getBody()).containsEntry("status", "TECHNICAL_OPENED");

        // Opened bids are for procurement staff and cleared committee members only.
        assertThat(as(PMO).get().uri(base + "/bids/" + bidId + "/files/" + techFile).retrieve()
                .toEntity(byte[].class).getStatusCode().value()).isEqualTo(403);
        byte[] tech = as(PO).get().uri(base + "/bids/" + bidId + "/files/" + techFile).retrieve().body(byte[].class);
        assertThat(new String(tech, StandardCharsets.UTF_8)).isEqualTo("TECHNICAL CONTENT");
        assertThat(as(PO).get().uri(base + "/bids/" + bidId + "/files/" + finFile).retrieve()
                .toEntity(byte[].class).getStatusCode().value()).isEqualTo(409);

        // Financial envelopes wait for the technical evaluation (see EvaluationApiIntegrationTest).
        Map<String, Object> early = post(PO, base + "/openings",
                Map.of("envelope", "FINANCIAL", "attendeeIds", List.of(PO, HOP, HOD, AO))).getBody();
        assertThat(String.valueOf(early.get("detail"))).contains("technical evaluation");
        Map<String, Object> detail = as(PO).get().uri(base).retrieve().body(MAP);
        assertThat(detail.get("openingBlockedReason")).isNotNull();

        List<Map<String, Object>> history = as(PO).get().uri(base + "/history").retrieve()
                .body(new ParameterizedTypeReference<>() { });
        assertThat(history).extracting(h -> h.get("action")).contains("tender.created", "tender.published",
                "bid.received", "tender.bids_opened", "bid.file_downloaded");
    }
}
