package com.pmplatform.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import com.pmplatform.testsupport.MutableClock;
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
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Project objectives: listed when the project is created and shaped freely during initiation; baselined after
 * gate G0 (only the PMO changes them, with a reason, and withdraws rather than deletes); then assessed one by one
 * in a post-implementation review, whose overall "objectives met" follows from the outcomes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pmp.storage.type=memory")
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class ObjectivesApiIntegrationTest {

    private static final String PMO = "01920000-0000-7000-8000-000000000101";
    private static final String PM = "01920000-0000-7000-8000-000000000102";
    private static final String HOD = "01920000-0000-7000-8000-000000000103";
    /** Smart metering, in execution, with three sample objectives and two benefits. */
    private static final String METERING = "01920000-0000-7000-8000-000000001003";
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

    private LocalDate today() {
        return LocalDate.ofInstant(Instant.now(clock), EAT);
    }

    @Test
    void objectivesListedAtCreationAreShapedDuringInitiation() {
        Map<String, Object> req = new HashMap<>();
        req.put("code", "OBJ-2026-001");
        req.put("name", "Objectives sample");
        req.put("budgetMinor", 100_000);
        req.put("objectives", List.of(
                Map.of("statement", "Restore supply to the town within 4 hours of a fault",
                        "successMeasure", "Average restoration time under 4 hours"),
                Map.of("statement", "  "), // an empty row on the form is skipped
                Map.of("statement", "Reduce outages")));
        String id = (String) ok(post(PM, "/projects", req)).get("id");
        String base = "/projects/" + id + "/objectives";

        Map<String, Object> v = ok(get(PM, base));
        assertThat(list(v, "objectives")).extracting(o -> o.get("number") + " " + o.get("statement"))
                .containsExactly("1 Restore supply to the town within 4 hours of a fault", "2 Reduce outages");
        assertThat(v).containsEntry("canAdd", true).containsEntry("reasonRequired", false);

        // Shaped freely during initiation, by the PM, without a reason.
        Map<String, Object> o2 = list(v, "objectives").get(1);
        v = ok(put(PM, base + "/" + o2.get("id"), Map.of("statement", "Halve unplanned outages",
                "successMeasure", "Outages per month", "version", o2.get("version"))));
        assertThat(list(v, "objectives").get(1)).containsEntry("statement", "Halve unplanned outages");
        assertThat(status(put(PM, base + "/" + o2.get("id"), Map.of("statement", "Stale edit",
                "version", o2.get("version"))))).isEqualTo(409);
        v = ok(post(PM, base, Map.of("statement", "Train the depot staff")));
        String o3 = (String) list(v, "objectives").get(2).get("id");
        assertThat(list(v, "objectives").get(2)).containsEntry("canDelete", true);
        v = ok(delete(PM, base + "/" + o3));
        assertThat(list(v, "objectives")).hasSize(2);

        // Others cannot change them.
        assertThat(status(post(HOD, base, Map.of("statement", "Mine")))).isEqualTo(403);
        assertThat(ok(get(HOD, base))).containsEntry("canAdd", false);
    }

    @Test
    void afterGateZeroOnlyThePmoChangesThemAndWithReason() {
        String base = "/projects/" + METERING + "/objectives";
        Map<String, Object> v = ok(get(PM, base));
        assertThat(v).containsEntry("canAdd", false).containsEntry("reasonRequired", true);
        assertThat(list(v, "objectives")).hasSize(3);
        list(v, "objectives").forEach(o -> assertThat(o).containsEntry("canEdit", false));

        assertThat(status(post(PM, base, Map.of("statement", "Something new", "reason", "Board asked")))).isEqualTo(403);
        assertThat(status(post(PMO, base, Map.of("statement", "Something new")))).isEqualTo(409); // no reason
        v = ok(post(PMO, base, Map.of("statement", "Publish monthly consumption to customers",
                "reason", "Added by the steering committee")));
        Map<String, Object> o4 = list(v, "objectives").get(3);
        assertThat(o4).containsEntry("number", 4).containsEntry("canDelete", false);
        assertThat(status(delete(PMO, base + "/" + o4.get("id")))).isEqualTo(409); // withdraw instead

        assertThat(status(post(PMO, base + "/" + o4.get("id") + "/withdraw", Map.of("reason", " "))))
                .isEqualTo(400);
        v = ok(post(PMO, base + "/" + o4.get("id") + "/withdraw", Map.of("reason", "Out of scope after all")));
        assertThat(list(v, "objectives").get(3)).containsEntry("status", "WITHDRAWN")
                .containsEntry("withdrawnReason", "Out of scope after all").containsEntry("canReinstate", true);

        List<Map<String, Object>> history = as(PM).get().uri("/projects/" + METERING + "/history").retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() { });
        assertThat(history).extracting(h -> h.get("action"))
                .contains("project.objective_added", "project.objective_withdrawn");
    }

    @Test
    void theReviewSaysWhetherEachObjectiveWasMet() {
        String p = "/projects/" + METERING;
        Map<String, Object> v = ok(post(PM, p + "/benefit-reviews", Map.of("title", "Objectives review",
                "dueDate", today().plusDays(10).toString(), "leadId", PM)));
        List<Map<String, Object>> active = list(v, "objectives");
        assertThat(active.size()).isGreaterThanOrEqualTo(3);
        Map<String, Object> review = list(v, "reviews").stream()
                .filter(r -> "Objectives review".equals(r.get("title"))).findFirst().orElseThrow();
        String r = p + "/benefit-reviews/" + review.get("id");

        Map<String, Object> findings = new HashMap<>();
        findings.put("conductedOn", today().toString());
        findings.put("objectivesMet", "YES"); // overridden by the outcomes below
        findings.put("summary", "Billing objective met; losses still falling");
        findings.put("assessments", list(v, "benefits").stream()
                .map(b -> Map.of("benefitId", b.get("id"), "rating", "ON_TRACK")).toList());
        findings.put("objectiveAssessments", List.of(
                Map.of("objectiveId", active.get(0).get("id"), "outcome", "MET", "evidence", "4% estimated bills")));
        v = ok(put(PM, r + "/findings", findings));
        assertThat(status(post(PM, r + "/complete", findings))).isEqualTo(409); // O2, O3 not assessed

        findings.put("objectiveAssessments", active.stream().map(o -> Map.of("objectiveId", o.get("id"),
                "outcome", ((Integer) o.get("number")) == 1 ? "MET" : ((Integer) o.get("number")) == 2 ? "TOO_EARLY"
                        : "NOT_MET", "evidence", "From the review")).toList());
        v = ok(post(PM, r + "/complete", findings));
        Map<String, Object> done = list(v, "reviews").stream().filter(x -> x.get("id").equals(review.get("id")))
                .findFirst().orElseThrow();
        assertThat(done).containsEntry("status", "COMPLETED").containsEntry("objectivesMet", "PARTLY");
        assertThat(list(done, "objectiveAssessments").subList(0, 3)).extracting(a -> a.get("outcome"))
                .containsExactly("MET", "TOO_EARLY", "NOT_MET");

        // The objectives now show what the review found, and an assessed objective cannot be deleted.
        Map<String, Object> obj = ok(get(PMO, p + "/objectives"));
        assertThat(list(obj, "objectives").get(0)).containsEntry("latestOutcome", "MET")
                .containsEntry("latestReview", "Objectives review");
    }
}
