package com.pmplatform.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.pmplatform.testsupport.MutableClock;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * A contract end to end: an RFQ is awarded (quick path through evaluation), then the contract is
 * drafted, refused signature until ready and past the standstill, signed, paid by milestone with
 * retention, varied within the 25% cap, completed with a rating, and its retention released.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pmp.storage.type=memory")
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class ContractApiIntegrationTest {

    private static final String REQUESTER = "01920000-0000-7000-8000-000000000102";
    private static final String HOD = "01920000-0000-7000-8000-000000000103";
    private static final String HOP = "01920000-0000-7000-8000-000000000104";
    private static final String AO = "01920000-0000-7000-8000-000000000105";
    private static final String PO = "01920000-0000-7000-8000-000000000106";
    private static final String CHAIR = "01920000-0000-7000-8000-000000000107";
    private static final String M2 = "01920000-0000-7000-8000-000000000108";
    private static final String M3 = "01920000-0000-7000-8000-000000000109";
    private static final String MANAGER = "01920000-0000-7000-8000-000000000112";
    private static final String FINANCE = "01920000-0000-7000-8000-000000000113";
    private static final String PROJECT = "01920000-0000-7000-8000-000000001003";
    private static final String SUPPLIER = "01920000-0000-7000-8000-000000005001";
    private static final ZoneId EAT = ZoneId.of("Africa/Nairobi");
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

    private ResponseEntity<Map<String, Object>> multipart(String user, String path, MultiValueMap<String, Object> parts) {
        return as(user).post().uri(path).contentType(MediaType.MULTIPART_FORM_DATA).body(parts).retrieve().toEntity(MAP);
    }

    private static int status(ResponseEntity<?> r) {
        return r.getStatusCode().value();
    }

    private static Map<String, Object> ok(ResponseEntity<Map<String, Object>> r) {
        assertThat(r.getStatusCode().value()).as(String.valueOf(r.getBody())).isBetween(200, 201);
        return r.getBody();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> m, String key) {
        return (List<Map<String, Object>>) m.get(key);
    }

    private static ByteArrayResource file(String name, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return name;
            }
        };
    }

    private LocalDate today() {
        return LocalDate.ofInstant(Instant.now(clock), EAT);
    }

    /** RFQ for KES 1.2m, one invited bid at KES 1m, evaluated and awarded. Returns the tender id. */
    @SuppressWarnings("unchecked")
    private String awardedRfq() {
        Map<String, Object> req = ok(post(REQUESTER, "/requisitions", Map.of("projectId", PROJECT,
                "title", "Meter boxes", "lines", List.of(Map.of("description", "Meter box", "quantity", 100,
                        "unit", "each", "unitPriceMinor", 1_200_000L)))));
        String reqId = (String) req.get("id");
        post(REQUESTER, "/requisitions/" + reqId + "/submit", null);
        ok(post(HOD, "/requisitions/" + reqId + "/approve", null));

        String tenderId = (String) ok(post(PO, "/tenders", Map.of("requisitionId", reqId, "methodCode", "RFQ"))).get("id");
        String t = "/tenders/" + tenderId;
        MultiValueMap<String, Object> doc = new LinkedMultiValueMap<>();
        doc.add("file", file("rfq.pdf", "%PDF rfq"));
        ok(multipart(PO, t + "/documents", doc));
        ok(post(PO, t + "/invitations", Map.of("supplierIds", List.of(SUPPLIER))));
        ok(put(PO, t, Map.of("title", "Meter boxes", "closingAt", Instant.now(clock).plus(Duration.ofDays(8)).toString())));
        ok(post(PO, t + "/publish", null));
        MultiValueMap<String, Object> bid = new LinkedMultiValueMap<>();
        bid.add("supplierId", SUPPLIER);
        bid.add("single", file("quotation.pdf", "QUOTE 1,000,000"));
        ok(multipart(PO, t + "/bids", bid));
        clock.advance(Duration.ofDays(9));
        Map<String, Object> opened = ok(post(PO, t + "/openings", Map.of("envelope", "SINGLE",
                "attendeeIds", List.of(PO, HOP, HOD))));
        String bidId = (String) list(opened, "bids").getFirst().get("id");
        ok(put(PO, t + "/bids/" + bidId + "/read-out", Map.of("priceMinor", 100_000_000L)));

        Map<String, Object> e = (Map<String, Object>) ok(post(PO, t + "/evaluation", Map.of())).get("evaluation");
        ok(put(PO, t + "/evaluation/committee", Map.of("members", List.of(
                Map.of("userId", CHAIR, "role", "CHAIR"), Map.of("userId", M2, "role", "MEMBER"),
                Map.of("userId", M3, "role", "MEMBER"), Map.of("userId", PO, "role", "SECRETARY")))));
        ok(post(AO, t + "/evaluation/appoint", null));
        for (String m : List.of(CHAIR, M2, M3, PO)) {
            ok(post(m, t + "/evaluation/declaration", Map.of("conflict", false)));
        }
        List<Map<String, Object>> marks = list(e, "criteria").stream()
                .map(c -> Map.<String, Object>of("criterionId", c.get("id"), "passed", true)).toList();
        for (String m : List.of(CHAIR, M2, M3)) {
            ok(put(m, t + "/evaluation/marks", Map.of("bidId", bidId, "marks", marks)));
        }
        ok(post(CHAIR, t + "/evaluation/close-stage", null));   // preliminary -> financial (no technical criteria)
        ok(put(CHAIR, t + "/evaluation/bids/" + bidId + "/price", Map.of("evaluatedPriceMinor", 100_000_000L)));
        ok(post(CHAIR, t + "/evaluation/close-stage", null));   // financial -> report
        ok(post(CHAIR, t + "/evaluation/report", Map.of("bidId", bidId, "noAward", false,
                "recommendation", "Award to the only responsive quotation")));
        ok(post(CHAIR, t + "/evaluation/sign", Map.of()));
        ok(post(PO, t + "/evaluation/sign", Map.of()));
        Map<String, Object> done = (Map<String, Object>) ok(post(HOP, t + "/evaluation/decision",
                Map.of("decision", "APPROVE", "note", "Concur"))).get("evaluation");
        assertThat(done).containsEntry("status", "AWARDED");
        return tenderId;
    }

    @Test
    void contractFromAwardToRetentionRelease() {
        String tenderId = awardedRfq();

        // ---- Draft
        assertThat(status(post(REQUESTER, "/contracts", Map.of("tenderId", tenderId)))).isEqualTo(403);
        Map<String, Object> c = ok(post(PO, "/contracts", Map.of("tenderId", tenderId)));
        assertThat(c).containsEntry("status", "DRAFT");
        assertThat(((Number) c.get("currentValueMinor")).longValue()).isEqualTo(100_000_000L);
        assertThat(list(c, "signingProblems")).isNotEmpty();
        assertThat(status(post(PO, "/contracts", Map.of("tenderId", tenderId)))).isEqualTo(409);
        String base = "/contracts/" + c.get("id");

        Map<String, Object> edit = new HashMap<>();
        edit.put("title", "Supply of meter boxes");
        edit.put("startDate", today().toString());
        edit.put("endDate", today().plusDays(90).toString());
        edit.put("retentionBp", 1000);
        edit.put("defectsLiabilityDays", 30);
        edit.put("performanceSecurityMinor", 10_000_000L);
        edit.put("contractManagerId", MANAGER);
        ok(put(PO, base, edit));
        c = ok(put(PO, base + "/milestones", Map.of("milestones", List.of(
                Map.of("title", "Delivery of 60 boxes", "amountMinor", 60_000_000L),
                Map.of("title", "Delivery of 40 boxes", "amountMinor", 40_000_000L)))));
        assertThat(((Number) c.get("scheduledMinor")).longValue()).isEqualTo(100_000_000L);

        MultiValueMap<String, Object> sec = new LinkedMultiValueMap<>();
        sec.add("kind", "PERFORMANCE");
        sec.add("form", "BANK_GUARANTEE");
        sec.add("amountMinor", "10000000");
        sec.add("issuer", "Sample Bank");
        sec.add("reference", "PG-2026-001");
        sec.add("expiryDate", today().plusDays(150).toString());
        sec.add("receivedOn", today().toString());
        ok(multipart(PO, base + "/securities", sec));
        MultiValueMap<String, Object> signed = new LinkedMultiValueMap<>();
        signed.add("kind", "SIGNED_CONTRACT");
        signed.add("file", file("contract.pdf", "%PDF signed"));
        c = ok(multipart(PO, base + "/documents", signed));

        // Only the standstill period is left; signing is refused until it ends.
        assertThat(list(c, "signingProblems")).hasSize(1);
        Map<String, Object> signing = Map.of("signedOn", today().toString(), "supplierSignatory", "J. Supplier, MD",
                "entitySignatory", "Accounting Officer");
        assertThat(status(post(PO, base + "/sign", signing))).isEqualTo(409);
        clock.advance(Duration.ofDays(15));
        signing = Map.of("signedOn", today().toString(), "supplierSignatory", "J. Supplier, MD",
                "entitySignatory", "Accounting Officer");
        c = ok(post(PO, base + "/sign", signing));
        assertThat(c).containsEntry("status", "ACTIVE");
        String m1 = (String) list(c, "milestones").get(0).get("id");
        String m2 = (String) list(c, "milestones").get(1).get("id");

        // ---- Invoice, acceptance, certificate, approval, payment
        MultiValueMap<String, Object> inv = new LinkedMultiValueMap<>();
        inv.add("supplierRef", "INV-001");
        inv.add("invoiceDate", today().toString());
        inv.add("milestoneId", m1);
        inv.add("grossMinor", "60000000");
        assertThat(status(multipart(REQUESTER, base + "/invoices", inv))).isEqualTo(403);
        c = ok(multipart(MANAGER, base + "/invoices", inv));
        Map<String, Object> invoice = list(c, "invoices").getFirst();
        assertThat(((Number) invoice.get("retentionMinor")).longValue()).isEqualTo(6_000_000L);
        String i1 = (String) invoice.get("id");
        assertThat(status(post(MANAGER, base + "/invoices/" + i1 + "/certify", Map.of("note", "Checked")))).isEqualTo(409);

        MultiValueMap<String, Object> accept = new LinkedMultiValueMap<>();
        accept.add("acceptedOn", today().toString());
        accept.add("note", "60 boxes inspected at Ruaraka stores, GRN 4411");
        ok(multipart(MANAGER, base + "/milestones/" + m1 + "/accept", accept));
        ok(post(MANAGER, base + "/invoices/" + i1 + "/certify", Map.of("note", "GRN 4411 matches")));
        assertThat(status(post(MANAGER, base + "/invoices/" + i1 + "/approve", null))).isEqualTo(403);
        ok(post(FINANCE, base + "/invoices/" + i1 + "/approve", null));
        c = ok(post(FINANCE, base + "/invoices/" + i1 + "/pay", Map.of("paidOn", today().toString(), "paymentRef", "EFT-1")));
        @SuppressWarnings("unchecked")
        Map<String, Object> totals = (Map<String, Object>) c.get("totals");
        assertThat(((Number) totals.get("paidMinor")).longValue()).isEqualTo(54_000_000L);
        assertThat(((Number) totals.get("retentionHeldMinor")).longValue()).isEqualTo(6_000_000L);

        MultiValueMap<String, Object> tooMuch = new LinkedMultiValueMap<>();
        tooMuch.add("invoiceDate", today().toString());
        tooMuch.add("milestoneId", m2);
        tooMuch.add("grossMinor", "50000000");
        assertThat(status(multipart(MANAGER, base + "/invoices", tooMuch))).isEqualTo(409);

        // ---- Variation: over the 25% cap refused; 20% approved by the accounting officer.
        assertThat(status(post(MANAGER, base + "/variations", Map.of("valueChangeMinor", 30_000_000L,
                "reason", "More boxes")))).isEqualTo(409);
        c = ok(post(MANAGER, base + "/variations", Map.of("valueChangeMinor", 20_000_000L,
                "newEndDate", today().plusDays(120).toString(), "reason", "20 more boxes for the new estate")));
        String v1 = (String) list(c, "variations").getFirst().get("id");
        assertThat(status(post(MANAGER, base + "/variations/" + v1 + "/decision", Map.of("approve", true)))).isEqualTo(403);
        c = ok(post(AO, base + "/variations/" + v1 + "/decision", Map.of("approve", true)));
        assertThat(((Number) c.get("currentValueMinor")).longValue()).isEqualTo(120_000_000L);
        assertThat(list(c, "warnings")).anyMatch(w -> String.valueOf(w).contains("payment schedule"));

        c = ok(put(MANAGER, base + "/milestones", Map.of("milestones", List.of(
                Map.of("id", m2, "title", "Delivery of 40 boxes", "amountMinor", 40_000_000L),
                Map.of("title", "Delivery of 20 more boxes", "amountMinor", 20_000_000L)))));
        assertThat(((Number) c.get("scheduledMinor")).longValue()).isEqualTo(120_000_000L);
        String m3 = (String) list(c, "milestones").get(2).get("id");

        // ---- Completion with rating, then retention release after the defects period.
        Map<String, Object> complete = Map.of("completedOn", today().toString(), "quality", 4, "timeliness", 3,
                "compliance", 5, "comment", "Delivered in full");
        assertThat(status(post(MANAGER, base + "/complete", complete))).isEqualTo(409);
        for (String m : List.of(m2, m3)) {
            MultiValueMap<String, Object> a = new LinkedMultiValueMap<>();
            a.add("acceptedOn", today().toString());
            a.add("note", "Inspected");
            ok(multipart(MANAGER, base + "/milestones/" + m + "/accept", a));
        }
        c = ok(post(MANAGER, base + "/complete", complete));
        assertThat(c).containsEntry("status", "COMPLETED");

        assertThat(status(post(MANAGER, base + "/retention-release", null))).isEqualTo(409);
        clock.advance(Duration.ofDays(31));
        c = ok(post(MANAGER, base + "/retention-release", null));
        Map<String, Object> release = list(c, "invoices").getLast();
        assertThat(release).containsEntry("kind", "RETENTION_RELEASE");
        assertThat(((Number) release.get("grossMinor")).longValue()).isEqualTo(6_000_000L);

        List<Map<String, Object>> perf = as(PO).get().uri("/contracts/supplier-performance").retrieve()
                .body(new ParameterizedTypeReference<>() { });
        assertThat(perf).anyMatch(p -> SUPPLIER.equals(p.get("supplierId")) && p.get("averageRating") != null);

        List<Map<String, Object>> history = as(PO).get().uri(base + "/history").retrieve()
                .body(new ParameterizedTypeReference<>() { });
        assertThat(history).extracting(h -> h.get("action")).contains("contract.created", "contract.signed",
                "contract.milestone_accepted", "contract.invoice_certified", "contract.payment_made",
                "contract.variation_requested", "contract.variation_decision", "contract.completed",
                "contract.retention_release");
    }
}
