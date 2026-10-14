package com.pmplatform.planning;

import static org.assertj.core.api.Assertions.assertThat;

import com.pmplatform.testsupport.MutableClock;
import java.io.IOException;
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
 * Planning and execution end to end: schedule with critical path, baseline and re-baseline,
 * progress by a project manager and an activity owner, MS Project import, risks, issues, a status
 * report through PMO review, and portfolio health.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pmp.storage.type=memory")
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class PlanningApiIntegrationTest {

    private static final String PMO = "01920000-0000-7000-8000-000000000101";
    private static final String PM = "01920000-0000-7000-8000-000000000102";
    private static final String HOD = "01920000-0000-7000-8000-000000000103";
    private static final String HOP = "01920000-0000-7000-8000-000000000104";
    private static final String PROJECT = "01920000-0000-7000-8000-000000001003";
    private static final String OTHER_PROJECT = "01920000-0000-7000-8000-000000001004";
    private static final ZoneId EAT = ZoneId.of("Africa/Nairobi");
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() { };

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

    private static Map<String, Object> row(Map<String, Object> view, String name) {
        return list(view, "activities").stream().filter(a -> name.equals(a.get("name"))).findFirst().orElseThrow();
    }

    private static Map<String, Object> activity(String key, String parent, String name, int days, List<Map<String, Object>> preds) {
        Map<String, Object> a = new HashMap<>();
        a.put("key", key);
        a.put("parentKey", parent);
        a.put("name", name);
        a.put("durationDays", days);
        a.put("predecessors", preds);
        return a;
    }

    private static Map<String, Object> pred(String key, String type, int lag) {
        return Map.of("key", key, "type", type, "lagDays", lag);
    }

    private LocalDate today() {
        return LocalDate.ofInstant(Instant.now(clock), EAT);
    }

    @Test
    @SuppressWarnings("unchecked")
    void planProgressRisksIssuesAndReports() throws IOException {
        String base = "/projects/" + PROJECT + "/schedule";
        Map<String, Object> empty = as(PM).get().uri(base).retrieve().body(MAP);
        assertThat(empty).containsEntry("exists", false);
        assertThat(map(empty, "permissions")).containsEntry("canEdit", true);

        LocalDate start = today().minusDays(10);
        List<Map<String, Object>> rows = List.of(
                activity("phase", null, "Line construction", 0, List.of()),
                activity("survey", "phase", "Survey", 5, List.of()),
                activity("design", "phase", "Design", 10, List.of(pred("survey", "FS", 0))),
                activity("wayleave", "phase", "Wayleave consent", 8, List.of(pred("survey", "SS", 2))),
                activity("build", "phase", "Build", 20, List.of(pred("design", "FS", 0), pred("wayleave", "FS", 0))),
                activity("done", null, "Commissioned", 0, List.of(pred("build", "FS", 0))));
        Map<String, Object> plan = Map.of("startDate", start.toString(), "activities", rows);

        assertThat(status(put(HOD, base, plan))).isEqualTo(403);
        Map<String, Object> s = ok(put(PM, base, plan));
        assertThat(map(s, "metrics")).containsEntry("plannedFinish", start.plusDays(35).toString());
        assertThat(row(s, "Design")).containsEntry("critical", true);
        assertThat(row(s, "Wayleave consent")).containsEntry("totalFloat", 5);
        assertThat(row(s, "Build")).containsEntry("wbs", "1.4");

        // A loop is refused and nothing changes.
        List<Map<String, Object>> loop = new java.util.ArrayList<>(rows);
        loop.set(1, activity("survey", "phase", "Survey", 5, List.of(pred("build", "FS", 0))));
        assertThat(status(put(PM, base, Map.of("startDate", start.toString(), "version", s.get("version"),
                "activities", loop)))).isEqualTo(409);
        // Saving without the version once the schedule exists is refused (it could overwrite someone's changes).
        assertThat(status(put(PM, base, plan))).isEqualTo(409);

        // Keep ids on re-save; give "Design" an owner.
        List<Map<String, Object>> keyed = new java.util.ArrayList<>();
        Map<String, String> idOf = new HashMap<>();
        for (Map<String, Object> a : list(s, "activities")) {
            idOf.put((String) a.get("name"), (String) a.get("id"));
        }
        for (Map<String, Object> r : rows) {
            Map<String, Object> copy = new HashMap<>(r);
            copy.put("key", idOf.get((String) r.get("name")));
            copy.put("parentKey", r.get("parentKey") == null ? null : idOf.get("Line construction"));
            copy.put("predecessors", ((List<Map<String, Object>>) r.get("predecessors")).stream()
                    .map(p -> pred(idOf.get(rows.stream().filter(x -> x.get("key").equals(p.get("key"))).findFirst()
                            .orElseThrow().get("name")), (String) p.get("type"), (Integer) p.get("lagDays"))).toList());
            if ("Design".equals(r.get("name"))) {
                copy.put("ownerId", HOD);
            }
            keyed.add(copy);
        }
        s = ok(put(PM, base, Map.of("startDate", start.toString(), "version", s.get("version"), "activities", keyed)));
        assertThat(row(s, "Design").get("id")).isEqualTo(idOf.get("Design"));
        assertThat(map(row(s, "Design"), "owner")).containsEntry("id", HOD);

        // ---- Baseline, then re-baseline needs the PMO and a reason.
        s = ok(post(PM, base + "/baseline", Map.of()));
        assertThat(s).containsEntry("baselineCount", 1);
        assertThat(status(post(PM, base + "/baseline", Map.of("note", "again")))).isEqualTo(403);
        assertThat(status(post(PMO, base + "/baseline", Map.of()))).isEqualTo(409);
        s = ok(post(PMO, base + "/baseline", Map.of("note", "Wayleave delay accepted by the steering committee")));
        assertThat(s).containsEntry("baselineCount", 2);

        // ---- Progress: the PM records the survey; the design owner records design; others may not.
        String survey = idOf.get("Survey");
        String design = idOf.get("Design");
        assertThat(status(put(PM, base + "/activities/" + survey + "/progress", Map.of(
                "actualStart", start.toString(), "actualFinish", today().plusDays(1).toString())))).isEqualTo(409);
        ok(put(PM, base + "/activities/" + survey + "/progress", Map.of(
                "actualStart", start.toString(), "actualFinish", start.plusDays(4).toString())));
        assertThat(status(put(HOD, base + "/activities/" + survey + "/progress", Map.of("percent", 10)))).isEqualTo(403);
        assertThat(status(put(PM, base + "/activities/" + idOf.get("Line construction") + "/progress",
                Map.of("percent", 10)))).isEqualTo(409); // summary row
        s = ok(put(HOD, base + "/activities/" + design + "/progress", Map.of(
                "actualStart", start.plusDays(5).toString(), "percent", 50, "note", "Drawings at 50%")));
        Map<String, Object> m = map(s, "metrics");
        assertThat(((Number) m.get("earnedPct")).doubleValue()).isGreaterThan(0);
        assertThat(m.get("spi")).isNotNull();
        assertThat(list(s, "actualCurve")).hasSize(1);
        assertThat(list(s, "plannedCurve")).isNotEmpty();

        // Import is refused once progress exists; it works on another project.
        byte[] xml = getClass().getResourceAsStream("/planning/sample-msproject.xml").readAllBytes();
        MultiValueMap<String, Object> file = new LinkedMultiValueMap<>();
        file.add("file", new ByteArrayResource(xml) {
            @Override
            public String getFilename() {
                return "plan.xml";
            }
        });
        assertThat(status(as(PM).post().uri(base + "/import").contentType(MediaType.MULTIPART_FORM_DATA).body(file)
                .retrieve().toEntity(MAP))).isEqualTo(409);
        Map<String, Object> imported = ok(as(PM).post().uri("/projects/" + OTHER_PROJECT + "/schedule/import")
                .contentType(MediaType.MULTIPART_FORM_DATA).body(file).retrieve().toEntity(MAP));
        assertThat(list(imported, "activities")).hasSize(5);
        assertThat(list(imported, "notices")).isNotEmpty();
        assertThat(row(imported, "Route survey")).containsEntry("wbs", "1.1");

        // ---- Risks: anyone raises; owner, creator and managers update.
        String p = "/projects/" + PROJECT;
        Map<String, Object> riskReq = new HashMap<>(Map.of("title", "Wayleave objections in Kitengela", "category",
                "WAYLEAVE", "probability", 4, "impact", 5, "response", "MITIGATE", "ownerId", PM));
        Map<String, Object> risk = ok(post(HOD, p + "/risks", riskReq));
        assertThat(risk).containsEntry("level", "CRITICAL").containsEntry("number", 1).containsEntry("score", 20);
        String riskId = (String) risk.get("id");
        assertThat(status(put(HOP, p + "/risks/" + riskId, riskReq))).isEqualTo(403);
        riskReq.put("probability", 3);
        assertThat(ok(put(HOD, p + "/risks/" + riskId, riskReq))).containsEntry("level", "HIGH");

        // ---- Issues
        Map<String, Object> issueReq = new HashMap<>(Map.of("title", "Survey vehicle broken down", "priority", "HIGH"));
        Map<String, Object> issue = ok(post(HOD, p + "/issues", issueReq));
        assertThat(issue).containsEntry("status", "OPEN").containsEntry("number", 1);
        issueReq.put("status", "RESOLVED");
        assertThat(status(put(HOD, p + "/issues/" + issue.get("id"), issueReq))).isEqualTo(409);
        issueReq.put("resolution", "Hired a replacement vehicle");
        issueReq.put("riskId", riskId);
        assertThat(ok(put(HOD, p + "/issues/" + issue.get("id"), issueReq))).containsEntry("status", "RESOLVED")
                .containsEntry("riskNumber", 1);

        // ---- Status report: draft with suggested ratings, submit freezes figures, PMO reviews.
        assertThat(status(post(HOD, p + "/status-reports", Map.of("periodEnd", today().toString())))).isEqualTo(403);
        Map<String, Object> report = ok(post(PM, p + "/status-reports", Map.of("periodEnd", today().toString())));
        assertThat(report).containsEntry("status", "DRAFT");
        assertThat(map(report, "suggested")).containsEntry("risk", "AMBER"); // one high open risk
        String reportId = (String) report.get("id");
        assertThat(status(post(PM, p + "/status-reports/" + reportId + "/submit", null))).isEqualTo(409);
        ok(put(PM, p + "/status-reports/" + reportId, Map.of("periodEnd", today().toString(), "overall", "AMBER",
                "schedule", "AMBER", "cost", "GREEN", "scope", "GREEN", "risk", "AMBER",
                "summary", "Design progressing; wayleave consents behind plan")));
        report = ok(post(PM, p + "/status-reports/" + reportId + "/submit", null));
        assertThat(report).containsEntry("status", "SUBMITTED");
        assertThat(map(report, "figures")).containsEntry("frozen", true).containsEntry("openRisks", 1);
        assertThat(status(post(PM, p + "/status-reports/" + reportId + "/review", Map.of("note", "ok")))).isEqualTo(403);
        assertThat(ok(post(PMO, p + "/status-reports/" + reportId + "/review", Map.of("note", "Escalate wayleaves"))))
                .containsEntry("status", "REVIEWED");
        assertThat(status(post(PM, p + "/status-reports", Map.of("periodEnd", today().toString())))).isEqualTo(409);

        // ---- Portfolio health
        List<Map<String, Object>> health = as(PMO).get().uri("/portfolio/health").retrieve().body(LIST);
        Map<String, Object> h = health.stream().filter(x -> PROJECT.equals(x.get("projectId"))).findFirst().orElseThrow();
        assertThat(h).containsEntry("lastOverall", "AMBER").containsEntry("openRisks", 1).containsEntry("highRisks", 1);
        assertThat(h.get("earnedPct")).isNotNull();

        List<Map<String, Object>> history = as(PM).get().uri(p + "/history").retrieve().body(LIST);
        assertThat(history).extracting(x -> x.get("action")).contains("schedule.saved", "schedule.baselined",
                "schedule.rebaselined", "schedule.progress", "risk.raised", "issue.updated", "report.submitted",
                "report.reviewed");
    }
}
