package com.pmplatform.benefits;

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
 * Benefits realisation end to end on the sample substation project: the PM registers a benefit,
 * its owner records measurements (refused in the future, before the baseline or twice on a day),
 * the rating follows progress against the straight line to target, a post-implementation review
 * is planned, written up and completed with the figures frozen, and the benefit is closed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pmp.storage.type=memory")
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class BenefitApiIntegrationTest {

    private static final String PMO = "01920000-0000-7000-8000-000000000101";
    private static final String PM = "01920000-0000-7000-8000-000000000102";
    private static final String HOD = "01920000-0000-7000-8000-000000000103";
    private static final String OPS = "01920000-0000-7000-8000-000000000114";
    private static final String PROJECT = "01920000-0000-7000-8000-000000001002";
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> m, String key) {
        return (Map<String, Object>) m.get(key);
    }

    private static double num(Map<String, Object> m, String key) {
        return ((Number) m.get(key)).doubleValue();
    }

    private LocalDate today() {
        return LocalDate.ofInstant(Instant.now(clock), EAT);
    }

    private Map<String, Object> benefit(Object target, LocalDate targetDate) {
        Map<String, Object> b = new HashMap<>();
        b.put("title", "Fewer substation outages");
        b.put("category", "OPERATIONAL");
        b.put("objective", "Reliable supply to the industrial area");
        b.put("ownerId", OPS);
        b.put("measure", "Feeder availability");
        b.put("unit", "%");
        b.put("baselineValue", 90);
        b.put("baselineDate", today().minusDays(180).toString());
        b.put("targetValue", target);
        b.put("targetDate", targetDate.toString());
        b.put("frequency", "QUARTERLY");
        b.put("annualValueMinor", 10_000_000_000L);
        return b;
    }

    @Test
    void benefitFromRegisterToReviewAndClosure() {
        String base = "/projects/" + PROJECT;

        // ---- Register.
        assertThat(status(post(HOD, base + "/benefits", benefit(100, today().plusDays(180))))).isEqualTo(403);
        assertThat(status(post(PM, base + "/benefits", benefit(90, today().plusDays(180))))).isEqualTo(409);
        assertThat(status(post(PM, base + "/benefits", benefit(100, today().minusDays(200))))).isEqualTo(409);
        Map<String, Object> v = ok(post(PM, base + "/benefits", benefit(100, today().plusDays(180))));
        Map<String, Object> b = list(v, "benefits").getFirst();
        assertThat(b).containsEntry("number", 1).containsEntry("direction", "INCREASE").containsEntry("status", "ACTIVE");
        assertThat(b.get("rating")).isNull();
        String path = base + "/benefits/" + b.get("id");

        // ---- Measurements by the owner.
        String m = path + "/measurements";
        assertThat(status(post(HOD, m, Map.of("measuredOn", today().toString(), "value", 94)))).isEqualTo(403);
        assertThat(status(post(OPS, m, Map.of("measuredOn", today().plusDays(1).toString(), "value", 94))))
                .isEqualTo(409);
        assertThat(status(post(OPS, m, Map.of("measuredOn", today().minusDays(400).toString(), "value", 94))))
                .isEqualTo(409);
        ok(post(OPS, m, Map.of("measuredOn", today().minusDays(30).toString(), "value", 92, "source", "SCADA")));
        v = ok(post(OPS, m, Map.of("measuredOn", today().toString(), "value", 94, "source", "SCADA")));
        assertThat(status(post(OPS, m, Map.of("measuredOn", today().toString(), "value", 95)))).isEqualTo(409);
        b = list(v, "benefits").getFirst();
        // 4 of 10 points is 40%, against about 50% expected half way: at risk.
        assertThat(num(b, "progressPct")).isEqualTo(40.0);
        assertThat(b).containsEntry("rating", "AT_RISK").containsEntry("overdue", false);
        assertThat(list(b, "measurements")).hasSize(2);
        assertThat(num(map(v, "summary"), "realisedMinor")).isEqualTo(4_000_000_000.0);

        Map<String, Object> portfolio = ok(get(PMO, "/benefits"));
        Object benefitId = b.get("id");
        assertThat(list(portfolio, "benefits")).anyMatch(x -> benefitId.equals(x.get("id")));

        // ---- Post-implementation review.
        assertThat(status(post(PM, base + "/benefit-reviews/standard", Map.of("leadId", OPS)))).isEqualTo(409);
        v = ok(post(PM, base + "/benefit-reviews", Map.of("title", "6-month review",
                "dueDate", today().plusDays(30).toString(), "leadId", OPS)));
        Map<String, Object> review = list(v, "reviews").getFirst();
        String r = base + "/benefit-reviews/" + review.get("id");
        assertThat(status(post(HOD, r + "/complete", Map.of()))).isEqualTo(403);
        Map<String, Object> findings = new HashMap<>();
        findings.put("conductedOn", today().toString());
        findings.put("objectivesMet", "PARTLY");
        findings.put("recommendations", "Add a second transformer feed before the dry season");
        ok(put(OPS, r + "/findings", findings));
        assertThat(status(post(OPS, r + "/complete", findings))).isEqualTo(409); // no summary, B1 not assessed
        findings.put("summary", "Availability improving but behind the line to target");
        assertThat(status(post(OPS, r + "/complete", findings))).isEqualTo(409); // B1 not assessed
        findings.put("assessments", List.of(Map.of("benefitId", b.get("id"), "rating", "AT_RISK",
                "comment", "Two forced outages in August")));
        v = ok(post(OPS, r + "/complete", findings));
        review = list(v, "reviews").getFirst();
        assertThat(review).containsEntry("status", "COMPLETED").containsEntry("objectivesMet", "PARTLY");
        Map<String, Object> assessed = list(review, "assessments").getFirst();
        assertThat(num(assessed, "actualValue")).isEqualTo(94.0);
        assertThat(assessed).containsEntry("rating", "AT_RISK");
        assertThat(status(put(OPS, r + "/findings", findings))).isEqualTo(409); // frozen

        // ---- Closing and reopening.
        assertThat(status(post(OPS, path + "/close", Map.of("outcome", "ACHIEVED", "note", "x")))).isEqualTo(403);
        v = ok(post(PM, path + "/close", Map.of("outcome", "NOT_ACHIEVED",
                "note", "Replaced by the regional reliability programme")));
        assertThat(list(v, "benefits").getFirst()).containsEntry("status", "NOT_ACHIEVED");
        assertThat(status(post(OPS, m, Map.of("measuredOn", today().toString(), "value", 96)))).isEqualTo(409);
        String measurementId = (String) list(list(v, "benefits").getFirst(), "measurements").getFirst().get("id");
        assertThat(status(delete(OPS, m + "/" + measurementId))).isEqualTo(409); // closed
        assertThat(status(post(PM, path + "/reopen", null))).isEqualTo(403);
        v = ok(post(PMO, path + "/reopen", null));
        assertThat(list(v, "benefits").getFirst()).containsEntry("status", "ACTIVE");
        v = ok(delete(OPS, m + "/" + measurementId));
        assertThat(list(list(v, "benefits").getFirst(), "measurements")).hasSize(1);
    }
}
