package com.pmplatform.closeout;

import static org.assertj.core.api.Assertions.assertThat;

import com.pmplatform.platform.web.BusinessRuleException;
import com.pmplatform.platform.web.ForbiddenActionException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CloseoutRulesTest {

    static final UUID TENANT = UUID.randomUUID();
    static final UUID PROJECT = UUID.randomUUID();
    static final UUID PM = UUID.randomUUID();
    static final UUID OPS = UUID.randomUUID();
    static final Instant NOW = Instant.parse("2026-10-11T08:00:00Z");

    static void refused(Runnable r, Class<? extends RuntimeException> type) {
        try {
            r.run();
            throw new AssertionError("expected " + type.getSimpleName());
        } catch (RuntimeException e) {
            assertThat(type.isInstance(e)).as(e.toString()).isTrue();
        }
    }

    @Test
    void checklistItemsNeedEvidenceOrAReason() {
        ChecklistItem i = new ChecklistItem(TENANT, PROJECT, 1, NOW);
        i.define("As-built drawings received", null, null, true, NOW);
        refused(() -> i.done(PM, null, 0, NOW), BusinessRuleException.class);
        i.done(PM, "Received 3 sets", 1, NOW);
        assertThat(i.getStatus()).isEqualTo(ItemStatus.DONE);
        refused(() -> i.notApplicable(PM, "x", NOW), BusinessRuleException.class);
        i.reopen(NOW);
        refused(() -> i.notApplicable(PM, " ", NOW), BusinessRuleException.class);
        i.notApplicable(PM, "No new lines were built", NOW);
        assertThat(i.getStatus()).isEqualTo(ItemStatus.NOT_APPLICABLE);
    }

    @Test
    void handoverGoesToTheNamedOfficer() {
        Closeout c = new Closeout(TENANT, PROJECT, PM, NOW);
        refused(() -> c.submitHandover(PM, NOW), BusinessRuleException.class); // nothing named yet
        c.planHandover("Network Operations", OPS, LocalDate.of(2026, 10, 20), null, NOW);
        refused(() -> c.submitHandover(OPS, NOW), BusinessRuleException.class); // officer cannot hand over
        c.submitHandover(PM, NOW);
        refused(() -> c.planHandover("Other", OPS, null, null, NOW), BusinessRuleException.class);
        Closeout.Figures f = new Closeout.Figures(100, 90, 80, 70, null, null, BigDecimal.valueOf(100), null);
        refused(() -> c.acceptHandover(PM, null, f, NOW), ForbiddenActionException.class);
        refused(() -> c.rejectHandover(OPS, "", NOW), BusinessRuleException.class);
        c.rejectHandover(OPS, "Manuals missing", NOW);
        assertThat(c.getStatus()).isEqualTo(CloseoutStatus.OPEN);
        c.submitHandover(PM, NOW);
        c.acceptHandover(OPS, "All received", f, NOW);
        assertThat(c.getStatus()).isEqualTo(CloseoutStatus.HANDED_OVER);
        assertThat(c.frozenFigures().certifiedMinor()).isEqualTo(80L);
        c.close(PM, "Done", NOW);
        assertThat(c.getStatus()).isEqualTo(CloseoutStatus.CLOSED);
    }

    @Test
    void defectsAreCheckedBySomeoneElse() {
        Defect d = new Defect(TENANT, PROJECT, 1, OPS, NOW);
        d.describe(null, "Leaking transformer", null, "Site 12", DefectSeverity.MAJOR, LocalDate.of(2026, 10, 1), NOW);
        assertThat(d.isOverdue(LocalDate.of(2026, 10, 5))).isTrue();
        refused(() -> d.verify(OPS, null, NOW), BusinessRuleException.class);
        refused(() -> d.rectified(PM, " ", NOW), BusinessRuleException.class);
        d.rectified(PM, "Gasket replaced", NOW);
        refused(() -> d.verify(PM, null, NOW), BusinessRuleException.class);
        d.reopen("Still leaking", NOW);
        assertThat(d.getStatus()).isEqualTo(DefectStatus.OPEN);
        d.rectified(PM, "Tank replaced", NOW);
        d.verify(OPS, "Dry after a week", NOW);
        assertThat(d.getStatus()).isEqualTo(DefectStatus.CLOSED);
        refused(() -> d.waive(OPS, "x", NOW), BusinessRuleException.class);
    }

    @Test
    void adoptedLessonsSayWhatWillChange() {
        Lesson l = new Lesson(TENANT, PROJECT, PM, NOW);
        l.write(LessonKind.IMPROVE, LessonCategory.WAYLEAVE, null, "Start wayleaves early", "Crews idle", null, NOW);
        refused(() -> l.review(LessonStatus.ADOPTED, OPS, null, NOW), BusinessRuleException.class);
        l.review(LessonStatus.ADOPTED, OPS, "Added to the G1 checklist", NOW);
        refused(() -> l.write(LessonKind.IMPROVE, LessonCategory.WAYLEAVE, null, "t", "w", null, NOW),
                BusinessRuleException.class);
    }
}
