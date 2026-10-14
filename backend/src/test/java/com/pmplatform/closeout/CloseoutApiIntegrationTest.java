package com.pmplatform.closeout;

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
 * Closeout end to end on the sample project in execution (no contracts or schedule in this database):
 * the checklist is worked through with evidence, a critical defect holds the handover until it is put
 * right and checked, the receiving officer rejects and then accepts the handover, gate G4 opens, a
 * lesson is reviewed, and the PMO closes the project.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pmp.storage.type=memory")
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class CloseoutApiIntegrationTest {

    private static final String PMO = "01920000-0000-7000-8000-000000000101";
    private static final String PM = "01920000-0000-7000-8000-000000000102";
    private static final String HOD = "01920000-0000-7000-8000-000000000103";
    private static final String FINANCE = "01920000-0000-7000-8000-000000000113";
    private static final String OPS = "01920000-0000-7000-8000-000000000114";
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

    private ResponseEntity<Map<String, Object>> upload(String user, String path, String name) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(("%PDF " + name).getBytes(StandardCharsets.UTF_8)) {
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

    private LocalDate today() {
        return LocalDate.ofInstant(Instant.now(clock), EAT);
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Map<String, Object> m, String key) {
        return (List<String>) m.get(key);
    }

    private static Map<String, Object> handover(String officer) {
        Map<String, Object> h = new HashMap<>();
        h.put("receivingUnit", "Network Operations, Nairobi South");
        h.put("receivingOfficerId", officer);
        h.put("handoverDate", LocalDate.now().toString());
        h.put("note", "Smart meters and data concentrators");
        h.put("assets", List.of(Map.of("description", "Data concentrator unit", "quantity", 12, "unit", "each",
                "tag", "DCU-001 to DCU-012", "valueMinor", 360_000_000L)));
        return h;
    }

    @Test
    void closeoutFromChecklistToClosure() {
        String p = "/projects/" + PROJECT;
        String base = p + "/closeout";

        // ---- Start: the project manager starts closeout and gets the standard checklist.
        assertThat(ok(get(HOD, base))).containsEntry("exists", false);
        assertThat(status(post(HOD, base + "/start", null))).isEqualTo(403);
        Map<String, Object> c = ok(post(PM, base + "/start", null));
        assertThat(c).containsEntry("status", "OPEN");
        List<Map<String, Object>> items = list(c, "checklist");
        assertThat(items).hasSize(ChecklistDefaults.ITEMS.size());
        assertThat(status(post(PM, base + "/start", null))).isEqualTo(409);
        assertThat(list(c, "handoverBlockers")).isNotEmpty();

        // ---- Checklist: evidence where required, the responsible role may mark its own item.
        for (Map<String, Object> i : items) {
            String path = base + "/checklist/" + i.get("id");
            if ("FINANCE_OFFICER".equals(i.get("responsibleRole"))) {
                assertThat(status(post(HOD, path + "/done", null))).isEqualTo(403);
                ok(post(FINANCE, path + "/done", Map.of("note", "Added to the fixed asset register")));
            } else if (Boolean.TRUE.equals(i.get("evidenceRequired"))) {
                assertThat(status(post(PM, path + "/done", null))).isEqualTo(409);
                ok(upload(PM, base + "/documents?kind=EVIDENCE&refId=" + i.get("id"), "evidence.pdf"));
                ok(post(PM, path + "/done", null));
            } else if (((String) i.get("title")).startsWith("Wayleave")) {
                assertThat(status(post(PM, path + "/not-applicable", Map.of("note", "")))).isEqualTo(409);
                ok(post(PM, path + "/not-applicable", Map.of("note", "Meters installed on existing premises")));
            } else {
                ok(post(PM, path + "/done", null));
            }
        }

        // ---- A critical defect holds the handover until it is put right and checked by someone else.
        c = ok(post(OPS, base + "/defects", Map.of("title", "Concentrator at Site 4 not reporting",
                "severity", "CRITICAL", "location", "Site 4", "dueDate", today().plusDays(7).toString())));
        Map<String, Object> defect = list(c, "defects").getFirst();
        assertThat(defect).containsEntry("number", 1).containsEntry("status", "OPEN");
        String d = base + "/defects/" + defect.get("id");
        ok(upload(OPS, base + "/documents?kind=DEFECT&refId=" + defect.get("id"), "photo.jpg"));

        assertThat(status(put(PM, base + "/handover", handover(PM)))).isEqualTo(409); // not yourself
        c = ok(put(PM, base + "/handover", handover(OPS)));
        assertThat(map(c, "handover")).containsEntry("assetValueMinor", 360_000_000);
        assertThat(strings(c, "handoverBlockers")).anyMatch(b -> b.contains("critical defect"));
        assertThat(status(post(PM, base + "/handover/submit", null))).isEqualTo(409);

        assertThat(status(post(HOD, d + "/rectified", Map.of("note", "Replaced")))).isEqualTo(403);
        ok(post(PM, d + "/rectified", Map.of("note", "Modem replaced, unit reporting")));
        assertThat(status(post(PM, d + "/verify", null))).isEqualTo(409); // same person
        c = ok(post(OPS, d + "/verify", Map.of("note", "Reporting for 48 hours")));
        assertThat(list(c, "defects").getFirst()).containsEntry("status", "CLOSED");
        assertThat(list(c, "handoverBlockers")).isEmpty();
        ok(upload(PM, base + "/documents?kind=HANDOVER", "as-built.pdf"));

        // ---- Gate G3 is open; gate G4 waits for the accepted handover.
        ok(post(PM, p + "/advance", Map.of("decisionNote", "Practical completion certified")));
        assertThat(status(post(PM, p + "/advance", Map.of("decisionNote", "Closure")))).isEqualTo(409);

        // ---- Handover: rejected once, then accepted by the named receiving officer.
        c = ok(post(PM, base + "/handover/submit", null));
        assertThat(c).containsEntry("status", "HANDOVER_SUBMITTED");
        assertThat(status(post(PM, base + "/checklist/" + items.getFirst().get("id") + "/reopen", null)))
                .isEqualTo(409);
        assertThat(status(post(OPS, base + "/handover/reject", Map.of("note", "")))).isEqualTo(409);
        c = ok(post(OPS, base + "/handover/reject", Map.of("note", "Spare modems not delivered")));
        assertThat(c).containsEntry("status", "OPEN");
        ok(post(PM, base + "/handover/submit", null));
        assertThat(status(post(HOD, base + "/handover/accept", null))).isEqualTo(403);
        // A critical defect reported after submission stops acceptance until it is dealt with.
        c = ok(post(HOD, base + "/defects", Map.of("title", "Cabinet door missing at Site 2", "severity", "CRITICAL")));
        String late = base + "/defects/" + list(c, "defects").get(1).get("id");
        assertThat(status(post(OPS, base + "/handover/accept", null))).isEqualTo(409);
        assertThat(status(put(HOD, late, Map.of("title", "Cabinet door missing at Site 2", "severity", "MINOR"))))
                .isEqualTo(403); // only the project team downgrades a defect
        ok(post(PMO, late + "/waive", Map.of("note", "Door supplied from stores and fitted the same day")));
        c = ok(post(OPS, base + "/handover/accept", Map.of("note", "Received in good order")));
        assertThat(c).containsEntry("status", "HANDED_OVER");
        assertThat(map(c, "figures")).containsEntry("frozen", true).containsEntry("budgetMinor", 92_000_000_000L);

        ok(post(PM, p + "/advance", Map.of("decisionNote", "Formal closure: handed over to Network Operations")));
        assertThat(ok(get(PM, p))).containsEntry("phase", "IMPLEMENTATION");

        // ---- A defect found in operation keeps the project open until it is dealt with.
        c = ok(post(OPS, base + "/defects", Map.of("title", "Meter seals missing at 3 premises", "severity", "MINOR")));
        String d2 = base + "/defects/" + list(c, "defects").get(2).get("id");
        assertThat(strings(c, "closeBlockers")).anyMatch(b -> b.contains("defect"));
        assertThat(status(post(PM, d2 + "/waive", Map.of("note", "Seals fitted by the utility")))).isEqualTo(403);
        ok(post(PMO, d2 + "/waive", Map.of("note", "Seals fitted by the utility's own crew")));

        // ---- Lessons learned and the PMO review.
        Map<String, Object> lesson = ok(post(HOD, p + "/lessons", Map.of("kind", "IMPROVE", "category", "PROCUREMENT",
                "title", "Order spare modems with the main supply", "whatHappened", "Spares came three months late")));
        assertThat(lesson).containsEntry("phase", "IMPLEMENTATION").containsEntry("status", "SUBMITTED");
        String review = "/lessons/" + lesson.get("id") + "/review";
        assertThat(status(post(HOD, review, Map.of("status", "ADOPTED", "note", "x")))).isEqualTo(403);
        assertThat(status(post(PMO, review, Map.of("status", "ADOPTED")))).isEqualTo(409);
        ok(post(PMO, review, Map.of("status", "ADOPTED", "note", "Spares added to the standard supply template")));
        List<Map<String, Object>> library = as(HOD).get().uri("/lessons?q=wayleave").retrieve().toEntity(LIST).getBody();
        assertThat(library).hasSize(1);

        // ---- Closing.
        assertThat(status(post(PM, base + "/close", null))).isEqualTo(403);
        c = ok(post(PMO, base + "/close", Map.of("note", "All obligations met")));
        assertThat(c).containsEntry("status", "CLOSED");
        assertThat(ok(get(PM, p))).containsEntry("status", "CLOSED");
        assertThat(status(post(OPS, base + "/defects", Map.of("title", "Late", "severity", "MINOR")))).isEqualTo(409);
    }
}
