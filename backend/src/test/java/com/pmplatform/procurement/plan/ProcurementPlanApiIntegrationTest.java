package com.pmplatform.procurement.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.pmplatform.testsupport.MutableClock;
import java.nio.charset.StandardCharsets;
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
 * The annual procurement plan end to end: a department is added and its head enters and imports
 * lines, submits within the budget ceiling, procurement submits the plan, the accounting officer
 * approves it; requisitions then draw on its lines (and are refused past a line's balance), or go
 * as unplanned with a reason and an extra accounting officer step; a revision raises a line.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pmp.storage.type=memory")
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class ProcurementPlanApiIntegrationTest {

    private static final String REQUESTER = "01920000-0000-7000-8000-000000000102";
    private static final String HOD = "01920000-0000-7000-8000-000000000103";
    private static final String HOP = "01920000-0000-7000-8000-000000000104";
    private static final String AO = "01920000-0000-7000-8000-000000000105";
    private static final String PO = "01920000-0000-7000-8000-000000000106";
    private static final String PROJECT = "01920000-0000-7000-8000-000000001003";
    private static final ZoneId EAT = ZoneId.of("Africa/Nairobi");
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST =
            new ParameterizedTypeReference<>() { };

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

    private ResponseEntity<Map<String, Object>> get(String user, String path) {
        return as(user).get().uri(path).retrieve().toEntity(MAP);
    }

    private ResponseEntity<Map<String, Object>> post(String user, String path, Object body) {
        return as(user).post().uri(path).contentType(MediaType.APPLICATION_JSON)
                .body(body == null ? Map.of() : body).retrieve().toEntity(MAP);
    }

    private ResponseEntity<Map<String, Object>> put(String user, String path, Object body) {
        return as(user).put().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(MAP);
    }

    private ResponseEntity<Map<String, Object>> delete(String user, String path) {
        return as(user).delete().uri(path).retrieve().toEntity(MAP);
    }

    private ResponseEntity<Map<String, Object>> upload(String user, String path, String name, String content) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return name;
            }
        });
        return as(user).post().uri(path).contentType(MediaType.MULTIPART_FORM_DATA).body(parts).retrieve()
                .toEntity(MAP);
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> m, String key) {
        return (Map<String, Object>) m.get(key);
    }

    private static long num(Map<String, Object> m, String key) {
        return ((Number) m.get(key)).longValue();
    }

    private static Map<String, Object> byField(List<Map<String, Object>> rows, String field, Object value) {
        return rows.stream().filter(r -> value.equals(r.get(field))).findFirst()
                .orElseThrow(() -> new AssertionError("no row with " + field + " = " + value));
    }

    private LocalDate today() {
        return LocalDate.ofInstant(Instant.now(clock), EAT);
    }

    /** The plan covering today: the dev sample (2026/27) or one created for the year the test runs in. */
    private String currentPlan() {
        List<Map<String, Object>> plans = as(HOP).get().uri("/procurement-plans").retrieve().toEntity(LIST).getBody();
        return plans.stream().filter(p -> Boolean.TRUE.equals(p.get("current"))).map(p -> (String) p.get("id"))
                .findFirst().orElseGet(() -> {
                    LocalDate t = today();
                    LocalDate fy = LocalDate.of(t.getMonthValue() >= 7 ? t.getYear() : t.getYear() - 1, 7, 1);
                    return (String) ok(post(HOP, "/procurement-plans", Map.of("fyStart", fy.toString()))).get("id");
                });
    }

    private static Map<String, Object> line(String deptId, String description, long estimateMinor, String method,
            int quarter) {
        Map<String, Object> m = new HashMap<>();
        m.put("departmentId", deptId);
        m.put("description", description);
        m.put("category", "GOODS");
        m.put("quantity", 1);
        m.put("unit", "lot");
        m.put("estimateMinor", estimateMinor);
        m.put("methodCode", method);
        m.put("quarter", quarter);
        return m;
    }

    private Map<String, Object> requisition(long amountMinor, String planLineId, String unplannedReason) {
        Map<String, Object> r = new HashMap<>();
        r.put("projectId", PROJECT);
        r.put("title", "Stores shelving");
        r.put("lines", List.of(Map.of("description", "Shelving", "quantity", 1, "unit", "lot",
                "unitPriceMinor", amountMinor)));
        r.put("planLineId", planLineId);
        r.put("unplannedReason", unplannedReason);
        return r;
    }

    @Test
    void planFromDepartmentsToApprovalAndRequisitionsAgainstIt() {
        String planId = currentPlan();
        String base = "/procurement-plans/" + planId;

        // ---- Procurement adds a department; its head prepares the lines.
        assertThat(status(post(REQUESTER, base + "/departments", Map.of("name", "Stores", "headId", HOD))))
                .isEqualTo(403);
        Map<String, Object> plan = ok(post(PO, base + "/departments",
                Map.of("name", "Stores", "headId", HOD, "ceilingMinor", 1_000_000_000L)));
        assertThat(status(post(PO, base + "/departments", Map.of("name", "stores", "headId", HOD)))).isEqualTo(409);
        String stores = (String) byField(list(plan, "departments"), "name", "Stores").get("id");

        assertThat(status(post(REQUESTER, base + "/lines", line(stores, "Shelving", 200_000_000L, "RFQ", 1))))
                .isEqualTo(403);
        assertThat(status(post(HOD, base + "/lines", line(stores, "Shelving", 500_000_000L, "RFQ", 1))))
                .isEqualTo(409); // RFQ only up to KES 3m in the sample settings
        plan = ok(post(HOD, base + "/lines", line(stores, "Shelving", 200_000_000L, "RFQ", 1)));
        String shelving = (String) byField(list(plan, "lines"), "description", "Shelving").get("id");

        Map<String, Object> bad = ok(upload(HOD, base + "/departments/" + stores + "/import", "bad.csv",
                "Description,Category,Estimate,Method,Quarter\nForklift,Goods,abc,Open tender,Q4\n"));
        assertThat(num(bad, "imported")).isZero();
        assertThat((List<?>) bad.get("errors")).isNotEmpty();
        Map<String, Object> imported = ok(upload(HOD, base + "/departments/" + stores + "/import", "stores.csv",
                "Description,Category,Qty,Unit,Estimate (KES),Method,Quarter,Reserved\n"
                        + "Forklift,Goods,1,each,\"4,000,000\",Open tender,Q4,Youth\n"));
        assertThat(num(imported, "imported")).isEqualTo(1);

        // ---- Ceiling: the department cannot submit more than its ceiling.
        ok(put(PO, base + "/departments/" + stores, Map.of("name", "Stores", "headId", HOD,
                "ceilingMinor", 500_000_000L)));
        assertThat(status(post(HOD, base + "/departments/" + stores + "/submit", null))).isEqualTo(409);
        ok(put(PO, base + "/departments/" + stores, Map.of("name", "Stores", "headId", HOD,
                "ceilingMinor", 1_000_000_000L)));
        ok(post(HOD, base + "/departments/" + stores + "/submit", null));
        assertThat(status(put(HOD, base + "/lines/" + shelving, line(stores, "Shelving", 210_000_000L, "RFQ", 1))))
                .isEqualTo(409); // submitted: only procurement may change it now

        // ---- Every department submits, then the head of procurement submits the plan.
        plan = ok(get(HOP, base));
        for (Map<String, Object> d : list(plan, "departments")) {
            if ("DRAFT".equals(d.get("status"))) {
                assertThat(status(post(HOP, base + "/submit", null))).isEqualTo(409);
                ok(post(HOP, base + "/departments/" + d.get("id") + "/submit", null));
            }
        }
        assertThat(status(post(PO, base + "/submit", null))).isEqualTo(403);
        plan = ok(post(HOP, base + "/submit", null));
        assertThat(plan).containsEntry("status", "SUBMITTED");
        assertThat(status(post(PO, base + "/lines", line(stores, "Desk", 10_000_000L, "RFQ", 2)))).isEqualTo(409);

        // ---- The accounting officer approves.
        assertThat(status(post(HOP, base + "/approve", null))).isEqualTo(403);
        plan = ok(post(AO, base + "/approve", Map.of("note", "Approved by the board")));
        assertThat(plan).containsEntry("status", "APPROVED").containsEntry("revision", 1);
        assertThat(num(byField(list(plan, "lines"), "id", shelving), "approvedEstimateMinor")).isEqualTo(200_000_000L);
        assertThat(list(plan, "revisions")).hasSize(1);

        List<Map<String, Object>> available = as(REQUESTER).get().uri("/procurement-plans/available-lines")
                .retrieve().toEntity(LIST).getBody();
        assertThat(byField(available, "id", shelving)).containsEntry("balanceMinor", 200_000_000);

        // ---- Requisitions draw on a line, up to its balance.
        Map<String, Object> r1 = ok(post(REQUESTER, "/requisitions", requisition(150_000_000L, shelving, null)));
        assertThat(r1).containsEntry("department", "Stores");
        r1 = ok(post(REQUESTER, "/requisitions/" + r1.get("id") + "/submit", null));
        assertThat(r1).containsEntry("planStatus", "PLANNED");
        // The requisition's own view leaves itself out of "other requisitions".
        assertThat(map(r1, "planLine")).containsEntry("committedMinor", 0).containsEntry("balanceMinor", 200_000_000);
        available = as(REQUESTER).get().uri("/procurement-plans/available-lines").retrieve().toEntity(LIST).getBody();
        assertThat(byField(available, "id", shelving)).containsEntry("balanceMinor", 50_000_000);

        Map<String, Object> r2 = ok(post(REQUESTER, "/requisitions", requisition(100_000_000L, shelving, null)));
        String r2Path = "/requisitions/" + r2.get("id");
        assertThat(status(post(REQUESTER, r2Path + "/submit", null))).isEqualTo(409); // over the balance

        // Unplanned: needs a reason, and the accounting officer joins the approval chain.
        ok(put(REQUESTER, r2Path, requisition(100_000_000L, null, null)));
        assertThat(status(post(REQUESTER, r2Path + "/submit", null))).isEqualTo(409);
        ok(put(REQUESTER, r2Path, requisition(100_000_000L, null,
                "Shelving collapsed in the Ruaraka store; replacement cannot wait for the revision")));
        r2 = ok(post(REQUESTER, r2Path + "/submit", null));
        assertThat(r2).containsEntry("planStatus", "UNPLANNED");
        assertThat(list(r2, "approvals")).extracting(a -> a.get("approverRole"))
                .contains("HEAD_OF_DEPARTMENT", "ACCOUNTING_OFFICER");

        plan = ok(get(AO, base));
        Map<String, Object> l1 = byField(list(plan, "lines"), "id", shelving);
        assertThat(num(l1, "committedMinor")).isEqualTo(150_000_000L);
        assertThat(l1).containsEntry("stage", "REQUISITIONED");
        assertThat(num(map(plan, "summary"), "unplannedCount")).isGreaterThanOrEqualTo(1);

        // ---- Approved lines cannot be deleted, and a line requisitions draw on cannot be dropped.
        assertThat(status(post(HOP, base + "/revise", Map.of("note", "short")))).isEqualTo(409);
        assertThat(status(post(AO, base + "/revise", Map.of("note", "Stores needs more shelving")))).isEqualTo(403);
        plan = ok(post(HOP, base + "/revise", Map.of("note", "Stores needs more shelving after the collapse")));
        assertThat(plan).containsEntry("status", "DRAFT");
        assertThat(status(delete(HOP, base + "/lines/" + shelving))).isEqualTo(409);
        assertThat(status(post(HOP, base + "/lines/" + shelving + "/cancel", Map.of("note", "Not needed"))))
                .isEqualTo(409);
        assertThat(status(put(HOP, base + "/lines/" + shelving, line(stores, "Shelving", 100_000_000L, "RFQ", 1))))
                .isEqualTo(409); // below what requisitions already draw

        // ---- Revision raises the line; until it is approved, requisitions still use the old amount.
        plan = ok(put(HOP, base + "/lines/" + shelving, line(stores, "Shelving", 260_000_000L, "RFQ", 1)));
        assertThat(byField(list(plan, "lines"), "id", shelving)).containsEntry("change", "CHANGED");
        Map<String, Object> r3 = ok(post(REQUESTER, "/requisitions", requisition(90_000_000L, shelving, null)));
        String r3Path = "/requisitions/" + r3.get("id");
        assertThat(status(post(REQUESTER, r3Path + "/submit", null))).isEqualTo(409);
        ok(post(HOP, base + "/submit", null));
        plan = ok(post(AO, base + "/approve", null));
        assertThat(plan).containsEntry("revision", 2);
        assertThat(list(plan, "revisions")).hasSize(2);
        r3 = ok(post(REQUESTER, r3Path + "/submit", null));
        assertThat(r3).containsEntry("planStatus", "PLANNED");
    }
}
