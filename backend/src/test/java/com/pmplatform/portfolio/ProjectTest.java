package com.pmplatform.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pmplatform.platform.web.BusinessRuleException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectTest {

    private final Instant now = Instant.parse("2026-10-09T10:00:00Z");

    private Project newProject() {
        return Project.create(UUID.randomUUID(), "NET-001", "Network extension", null, null,
                1_000_00L, "KES", now);
    }

    @Test
    void startsInInitiation() {
        Project p = newProject();
        assertThat(p.getPhase()).isEqualTo(ProjectPhase.INITIATION);
        assertThat(p.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
        assertThat(p.isNew()).isTrue();
    }

    @Test
    void passesGatesInOrder() {
        Project p = newProject();
        assertThat(p.advancePhase(now)).isEqualTo("G0");
        assertThat(p.getPhase()).isEqualTo(ProjectPhase.PROCUREMENT);
        assertThat(p.advancePhase(now)).isEqualTo("G1");
        assertThat(p.advancePhase(now)).isEqualTo("G2");
        assertThat(p.advancePhase(now)).isEqualTo("G3");
        assertThat(p.advancePhase(now)).isEqualTo("G4");
        assertThat(p.getPhase()).isEqualTo(ProjectPhase.IMPLEMENTATION);
    }

    @Test
    void cannotPassBeyondImplementation() {
        Project p = newProject();
        for (int i = 0; i < 5; i++) {
            p.advancePhase(now);
        }
        assertThatThrownBy(() -> p.advancePhase(now))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("last phase");
    }
}
