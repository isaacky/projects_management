package com.pmplatform.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditHasherTest {

    private final UUID tenant = UUID.fromString("01920000-0000-7000-8000-000000000001");
    private final UUID entity = UUID.fromString("01920000-0000-7000-8000-000000001001");
    private final Instant at = Instant.parse("2026-10-09T10:00:00Z");

    @Test
    void sameInputGivesSameHash() {
        String a = AuditHasher.hash(null, tenant, at, "alice", "project.created", "Project", entity, "{}");
        String b = AuditHasher.hash(null, tenant, at, "alice", "project.created", "Project", entity, "{}");
        assertThat(a).isEqualTo(b).hasSize(64);
    }

    @Test
    void anyChangeBreaksTheHash() {
        String original = AuditHasher.hash("abc", tenant, at, "alice", "project.created", "Project",
                entity, "{\"code\":\"X\"}");
        String tampered = AuditHasher.hash("abc", tenant, at, "alice", "project.created", "Project",
                entity, "{\"code\":\"Y\"}");
        String otherParent = AuditHasher.hash("abd", tenant, at, "alice", "project.created", "Project",
                entity, "{\"code\":\"X\"}");
        assertThat(tampered).isNotEqualTo(original);
        assertThat(otherParent).isNotEqualTo(original);
    }
}
