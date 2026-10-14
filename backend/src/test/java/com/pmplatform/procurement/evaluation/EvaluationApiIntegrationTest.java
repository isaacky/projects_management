package com.pmplatform.procurement.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.pmplatform.testsupport.MutableClock;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
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
 * Evaluation end to end on a two-envelope open tender with three bids: committee appointment,
 * declarations (one conflict), preliminary and technical stages with quorum, the financial
 * opening of the bid that passed, ranking, report, signatures, a returned report, the
 * professional opinion and the award.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pmp.storage.type=memory")
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class EvaluationApiIntegrationTest {

    private static final String PMO = "01920000-0000-7000-8000-000000000101";
    private static final String REQUESTER = "01920000-0000-7000-8000-000000000102";
    private static final String HOD = "01920000-0000-7000-8000-000000000103";
    private static final String HOP = "01920000-0000-7000-8000-000000000104";
    private static final String AO = "01920000-0000-7000-8000-000000000105";
    private static final String PO = "01920000-0000-7000-8000-000000000106";
    private static final String CHAIR = "01920000-0000-7000-8000-000000000107";
    private static final String M2 = "01920000-0000-7000-8000-000000000108";
    private static final String M3 = "01920000-0000-7000-8000-000000000109";
    private static final String M4 = "01920000-0000-7000-8000-000000000110";
    private static final String CONFLICTED = "01920000-0000-7000-8000-000000000111";
    private static final String PROJECT = "01920000-0000-7000-8000-000000001003";
    private static final String GOOD = "01920000-0000-7000-8000-000000005001";
    private static final String WEAK = "01920000-0000-7000-8000-000000005002";
    private static final String NO_SECURITY = "01920000-0000-7000-8000-000000005003";
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

    private ResponseEntity<Map<String, Object>> put(String user, String path, Object body) {
        return as(user).put().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(MAP);
    }

    private static int status(ResponseEntity<?> r) {
        return r.getStatusCode().value();
    }

    private static ByteArrayResource file(String name, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return name;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> evaluation(ResponseEntity<Map<String, Object>> r) {
        assertThat(r.getStatusCode().value()).as(String.valueOf(r.getBody())).isEqualTo(200);
        return (Map<String, Object>) r.getBody().get("evaluation");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> m, String key) {
        return (List<Map<String, Object>>) m.get(key);
    }

    private String bidOf(Map<String, Object> eval, String supplierId) {
        return (String) list(eval, "bids").stream()
                .filter(b -> supplierId.equals(((Map<?, ?>) b.get("supplier")).get("id")))
                .findFirst().orElseThrow().get("bidId");
    }

    /** Marks every preliminary criterion of a bid; {@code failName} (if any) is marked not met. */
    private ResponseEntity<Map<String, Object>> preliminary(String member, String base, Map<String, Object> eval,
            String bidId, String failName) {
        List<Map<String, Object>> marks = new ArrayList<>();
        for (Map<String, Object> c : list(eval, "criteria")) {
            if (!"PRELIMINARY".equals(c.get("stage"))) {
                continue;
            }
            boolean fail = c.get("name").equals(failName);
            Map<String, Object> m = new HashMap<>();
            m.put("criterionId", c.get("id"));
            m.put("passed", !fail);
            if (fail) {
                m.put("comment", "No tender security attached");
            }
            marks.add(m);
        }
        return put(member, base + "/evaluation/marks", Map.of("bidId", bidId, "marks", marks));
    }

    /** Scores every technical criterion at {@code pct} percent of its maximum. */
    private ResponseEntity<Map<String, Object>> technical(String member, String base, Map<String, Object> eval,
            String bidId, int pct) {
        List<Map<String, Object>> marks = new ArrayList<>();
        for (Map<String, Object> c : list(eval, "criteria")) {
            if ("TECHNICAL".equals(c.get("stage"))) {
                double max = ((Number) c.get("maxScore")).doubleValue();
                marks.add(Map.of("criterionId", c.get("id"), "score", max * pct / 100.0));
            }
        }
        return put(member, base + "/evaluation/marks", Map.of("bidId", bidId, "marks", marks));
    }

    private void bid(String base, String supplierId, String price) {
        MultiValueMap<String, Object> bid = new LinkedMultiValueMap<>();
        bid.add("supplierId", supplierId);
        bid.add("technical", file("technical.pdf", "TECHNICAL " + supplierId));
        bid.add("financial", file("price.pdf", "PRICE " + price));
        assertThat(status(as(PO).post().uri(base + "/bids").contentType(MediaType.MULTIPART_FORM_DATA).body(bid)
                .retrieve().toEntity(MAP))).isEqualTo(200);
    }

    @Test
    @SuppressWarnings("unchecked")
    void evaluationToAward() {
        // ---- An approved requisition, an open tender, three bids, technical opening.
        Map<String, Object> req = post(REQUESTER, "/requisitions", Map.of("projectId", PROJECT,
                "title", "Pole-mounted transformers",
                "lines", List.of(Map.of("description", "Transformer 50 kVA", "quantity", 4, "unit", "each",
                        "unitPriceMinor", 30_000_000L)))).getBody();
        String reqId = (String) req.get("id");
        post(REQUESTER, "/requisitions/" + reqId + "/submit", null);
        post(HOD, "/requisitions/" + reqId + "/approve", null);

        String tenderId = (String) post(PO, "/tenders", Map.of("requisitionId", reqId, "methodCode", "OPEN_TENDER"))
                .getBody().get("id");
        String base = "/tenders/" + tenderId;
        MultiValueMap<String, Object> doc = new LinkedMultiValueMap<>();
        doc.add("file", file("tender.pdf", "%PDF tender"));
        as(PO).post().uri(base + "/documents").contentType(MediaType.MULTIPART_FORM_DATA).body(doc).retrieve()
                .toBodilessEntity();
        Instant closing = Instant.now(clock).plus(Duration.ofDays(22));
        put(PO, base, Map.of("title", "Pole-mounted transformers", "closingAt", closing.toString()));
        post(PO, base + "/publish", null);
        bid(base, GOOD, "110,000,000");
        bid(base, WEAK, "90,000,000");
        bid(base, NO_SECURITY, "80,000,000");

        // Not before the bids are opened.
        Map<String, Object> notYet = as(PO).get().uri(base + "/evaluation").retrieve().body(MAP);
        assertThat(notYet).containsEntry("exists", false).containsEntry("canSetUp", false);

        clock.advance(Duration.ofDays(23));
        post(PO, base + "/openings", Map.of("envelope", "TECHNICAL", "attendeeIds", List.of(PO, HOP, HOD, AO)));

        // ---- Set-up by procurement, appointment by the accounting officer.
        Map<String, Object> ready = as(PO).get().uri(base + "/evaluation").retrieve().body(MAP);
        assertThat(ready).containsEntry("canSetUp", true);
        assertThat(status(post(REQUESTER, base + "/evaluation", Map.of()))).isEqualTo(403);

        Map<String, Object> eval = evaluation(post(PO, base + "/evaluation", Map.of()));
        assertThat(eval).containsEntry("status", "SETUP").containsEntry("templateCode", "OPEN_TENDER_STANDARD")
                .containsEntry("passMarkPct", 70).containsEntry("quorum", 4);
        assertThat(list(eval, "criteria")).hasSize(11);

        List<Map<String, Object>> committee = List.of(
                Map.of("userId", CHAIR, "role", "CHAIR"), Map.of("userId", M2, "role", "MEMBER"),
                Map.of("userId", M3, "role", "MEMBER"), Map.of("userId", M4, "role", "MEMBER"),
                Map.of("userId", CONFLICTED, "role", "MEMBER"), Map.of("userId", PO, "role", "SECRETARY"));
        evaluation(put(PO, base + "/evaluation/committee", Map.of("members", committee)));

        assertThat(status(post(HOP, base + "/evaluation/appoint", null))).isEqualTo(403);
        eval = evaluation(post(AO, base + "/evaluation/appoint", null));
        assertThat(eval).containsEntry("status", "PRELIMINARY");

        // Outsiders cannot see the evaluation; members must declare before marking or reading bids.
        assertThat(status(as(PMO).get().uri(base + "/evaluation").retrieve().toEntity(MAP))).isEqualTo(403);
        String good = bidOf(eval, GOOD);
        String weak = bidOf(eval, WEAK);
        String noSecurity = bidOf(eval, NO_SECURITY);
        assertThat(status(preliminary(M2, base, eval, good, null))).isEqualTo(403);

        for (String m : List.of(CHAIR, M2, M3, M4)) {
            evaluation(post(m, base + "/evaluation/declaration", Map.of("conflict", false)));
        }
        evaluation(post(CONFLICTED, base + "/evaluation/declaration",
                Map.of("conflict", true, "declaration", "I am a director of a bidder")));
        assertThat(status(preliminary(CONFLICTED, base, eval, good, null))).isEqualTo(403);

        Map<String, Object> tender = as(M2).get().uri(base).retrieve().body(MAP);
        Map<String, Object> goodBid = list(tender, "bids").stream().filter(b -> good.equals(b.get("id"))).findFirst()
                .orElseThrow();
        String techFile = (String) list(goodBid, "files").stream()
                .filter(f -> "TECHNICAL".equals(f.get("envelope"))).findFirst().orElseThrow().get("id");
        assertThat(status(as(M2).get().uri(base + "/bids/" + good + "/files/" + techFile).retrieve()
                .toEntity(byte[].class))).isEqualTo(200);
        assertThat(status(as(CONFLICTED).get().uri(base + "/bids/" + good + "/files/" + techFile).retrieve()
                .toEntity(byte[].class))).isEqualTo(403);

        // ---- Preliminary: three members say the third bid has no tender security.
        String security = "Tender security of the required form and amount";
        for (String m : List.of(CHAIR, M2, M3)) {
            evaluation(preliminary(m, base, eval, good, null));
            evaluation(preliminary(m, base, eval, weak, null));
            evaluation(preliminary(m, base, eval, noSecurity, security));
        }
        assertThat(status(post(CHAIR, base + "/evaluation/close-stage", null))).isEqualTo(409); // 3 < quorum 4
        assertThat(status(post(M2, base + "/evaluation/close-stage", null))).isEqualTo(403);    // chair only
        evaluation(preliminary(M4, base, eval, good, null));
        evaluation(preliminary(M4, base, eval, weak, null));
        evaluation(preliminary(M4, base, eval, noSecurity, null));
        eval = evaluation(post(CHAIR, base + "/evaluation/close-stage", null));
        assertThat(eval).containsEntry("status", "TECHNICAL");
        Map<String, Object> failed = list(eval, "bids").stream().filter(b -> noSecurity.equals(b.get("bidId")))
                .findFirst().orElseThrow();
        assertThat(failed).containsEntry("disqualifiedStage", "PRELIMINARY").containsEntry("preliminaryPassed", false);
        assertThat((String) failed.get("disqualifiedReason")).contains(security);

        // The financial envelopes stay closed during the technical stage.
        assertThat(status(post(PO, base + "/openings",
                Map.of("envelope", "FINANCIAL", "attendeeIds", List.of(PO, HOP, HOD, AO))))).isEqualTo(409);

        // ---- Technical: the good bid scores 80%, the weak one 60% (pass mark 70%).
        assertThat(status(technical(CHAIR, base, eval, noSecurity, 50))).isEqualTo(409); // out of the running
        for (String m : List.of(CHAIR, M2, M3, M4)) {
            evaluation(technical(m, base, eval, good, 80));
            evaluation(technical(m, base, eval, weak, 60));
        }
        eval = evaluation(post(CHAIR, base + "/evaluation/close-stage", null));
        assertThat(eval).containsEntry("status", "FINANCIAL");
        Map<String, Object> goodRow = list(eval, "bids").stream().filter(b -> good.equals(b.get("bidId")))
                .findFirst().orElseThrow();
        assertThat(((Number) goodRow.get("technicalPct")).doubleValue()).isEqualTo(80.0);
        assertThat(list(eval, "closedStageMarks")).isNotEmpty();

        // ---- Financial opening: only the bid that passed is opened.
        Map<String, Object> opened = post(PO, base + "/openings",
                Map.of("envelope", "FINANCIAL", "attendeeIds", List.of(PO, HOP, HOD, AO))).getBody();
        assertThat(opened).containsEntry("status", "OPENED");
        Map<String, Object> weakBid = list(opened, "bids").stream().filter(b -> weak.equals(b.get("id")))
                .findFirst().orElseThrow();
        String weakFin = (String) list(weakBid, "files").stream()
                .filter(f -> "FINANCIAL".equals(f.get("envelope"))).findFirst().orElseThrow().get("id");
        assertThat(status(as(PO).get().uri(base + "/bids/" + weak + "/files/" + weakFin).retrieve()
                .toEntity(byte[].class))).isEqualTo(409);
        assertThat(status(put(PO, base + "/bids/" + weak + "/read-out", Map.of("priceMinor", 1L)))).isEqualTo(409);
        assertThat(status(put(PO, base + "/bids/" + good + "/read-out", Map.of("priceMinor", 11_000_000_000L))))
                .isEqualTo(200);

        // Evaluated price differs from the read-out price: a correction note is required.
        assertThat(status(put(CHAIR, base + "/evaluation/bids/" + good + "/price",
                Map.of("evaluatedPriceMinor", 10_900_000_000L)))).isEqualTo(409);
        evaluation(put(CHAIR, base + "/evaluation/bids/" + good + "/price",
                Map.of("evaluatedPriceMinor", 10_900_000_000L, "note", "Arithmetic error in item 3 corrected")));
        eval = evaluation(post(CHAIR, base + "/evaluation/close-stage", null));
        assertThat(eval).containsEntry("status", "REPORT");
        assertThat(list(eval, "warnings")).anyMatch(w -> String.valueOf(w).contains("above the budget"));

        // ---- Report, signatures (the conflicted member and the secretary do not sign), approval.
        eval = evaluation(post(CHAIR, base + "/evaluation/report", Map.of("bidId", good, "noAward", false,
                "recommendation", "Award to the lowest evaluated responsive bidder")));
        assertThat(eval).containsEntry("status", "SIGNING");
        assertThat(list(eval, "signatories")).hasSize(4);
        for (String m : List.of(CHAIR, M2, M3)) {
            evaluation(post(m, base + "/evaluation/sign", Map.of()));
        }
        assertThat(status(post(CONFLICTED, base + "/evaluation/sign", Map.of()))).isEqualTo(403);
        eval = evaluation(post(M4, base + "/evaluation/sign", Map.of("note", "Signed")));
        assertThat(eval).containsEntry("status", "APPROVAL");
        assertThat(list(eval, "approvals")).extracting(a -> a.get("approverRole"))
                .containsExactly("HEAD_OF_PROCUREMENT", "ACCOUNTING_OFFICER");

        // The accounting officer cannot act before the professional opinion.
        assertThat(status(post(AO, base + "/evaluation/decision", Map.of("decision", "APPROVE")))).isEqualTo(403);
        assertThat(status(post(HOP, base + "/evaluation/decision", Map.of("decision", "APPROVE")))).isEqualTo(409);
        evaluation(post(HOP, base + "/evaluation/decision", Map.of("decision", "APPROVE",
                "note", "Concur: the evaluation followed the tender document and the Act")));

        // Returned once: the committee resubmits and signs again in round 2.
        eval = evaluation(post(AO, base + "/evaluation/decision", Map.of("decision", "RETURN",
                "note", "Confirm the arithmetic correction with the bidder")));
        assertThat(eval).containsEntry("status", "REPORT").containsEntry("round", 2);
        evaluation(post(CHAIR, base + "/evaluation/report", Map.of("bidId", good, "noAward", false,
                "recommendation", "Award; the bidder accepted the correction in writing")));
        for (String m : List.of(CHAIR, M2, M3, M4)) {
            evaluation(post(m, base + "/evaluation/sign", Map.of()));
        }
        evaluation(post(HOP, base + "/evaluation/decision", Map.of("decision", "APPROVE", "note", "Concur")));
        eval = evaluation(post(AO, base + "/evaluation/decision", Map.of("decision", "APPROVE")));
        assertThat(eval).containsEntry("status", "AWARDED");
        Map<String, Object> award = (Map<String, Object>) eval.get("award");
        assertThat(((Number) award.get("amountMinor")).longValue()).isEqualTo(10_900_000_000L);

        Map<String, Object> awarded = as(PO).get().uri(base).retrieve().body(MAP);
        assertThat(awarded).containsEntry("status", "AWARDED").containsEntry("awardedBidId", good);
        Instant standstill = Instant.parse((String) awarded.get("standstillEndsAt"));
        assertThat(Duration.between(Instant.parse((String) awarded.get("awardedAt")), standstill))
                .isEqualTo(Duration.ofDays(14));

        List<Map<String, Object>> history = as(PO).get().uri(base + "/history").retrieve()
                .body(new ParameterizedTypeReference<>() { });
        assertThat(history).extracting(h -> h.get("action")).contains("evaluation.set_up",
                "evaluation.committee_appointed", "evaluation.declaration", "evaluation.preliminary_closed",
                "evaluation.technical_closed", "evaluation.price_evaluated", "evaluation.report_submitted",
                "evaluation.report_signed", "evaluation.decision", "tender.awarded");
    }
}
