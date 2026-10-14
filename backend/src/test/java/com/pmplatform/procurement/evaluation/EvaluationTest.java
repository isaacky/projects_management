package com.pmplatform.procurement.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.pmplatform.platform.web.BusinessRuleException;
import com.pmplatform.procurement.config.EvaluationCommitteeRule;
import com.pmplatform.procurement.config.ReportSignatories;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvaluationTest {

    static final Instant NOW = Instant.parse("2026-10-10T08:00:00Z");
    static final UUID TENANT = UUID.randomUUID();
    static final UUID USER = UUID.randomUUID();

    static Evaluation evaluation() {
        EvaluationCommitteeRule rule = new EvaluationCommitteeRule(TENANT, "OPEN_TENDER", NOW);
        rule.update(5, 7, 4, true, 70, true, ReportSignatories.ALL_MEMBERS, "HEAD_OF_PROCUREMENT",
                "ACCOUNTING_OFFICER", NOW);
        return new Evaluation(TENANT, UUID.randomUUID(), rule, "OPEN_TENDER_STANDARD", USER, NOW);
    }

    static void expectRefused(Runnable r) {
        try {
            r.run();
        } catch (BusinessRuleException expected) {
            return;
        }
        throw new AssertionError("expected the action to be refused");
    }

    @Test
    void copiesTheCommitteeRule() {
        Evaluation e = evaluation();
        assertThat(e.getStatus()).isEqualTo(EvaluationStatus.SETUP);
        assertThat(e.getQuorum()).isEqualTo(4);
        assertThat(e.getTechnicalPassMarkPct()).isEqualTo(70);
        assertThat(e.getReportReviewerRole()).isEqualTo("HEAD_OF_PROCUREMENT");
    }

    @Test
    void runsThroughEveryStageToAward() {
        Evaluation e = evaluation();
        e.appoint(USER, NOW);
        assertThat(e.getStatus()).isEqualTo(EvaluationStatus.PRELIMINARY);
        expectRefused(() -> e.setPassMark(60, NOW));
        e.closePreliminary(true, true, NOW);
        assertThat(e.getStatus()).isEqualTo(EvaluationStatus.TECHNICAL);
        assertThat(e.technicalDone()).isFalse();
        e.closeTechnical(true, NOW);
        assertThat(e.technicalDone()).isTrue();
        e.closeFinancial(NOW);
        e.submitReport(USER, UUID.randomUUID(), 1_000L, false, "Award to the lowest evaluated bidder", NOW);
        assertThat(e.getStatus()).isEqualTo(EvaluationStatus.SIGNING);
        e.signed(NOW);
        e.returnToCommittee(NOW);
        assertThat(e.getStatus()).isEqualTo(EvaluationStatus.REPORT);
        assertThat(e.getRound()).isEqualTo(2);
        e.submitReport(USER, UUID.randomUUID(), 900L, false, "Revised", NOW);
        e.signed(NOW);
        e.award(NOW);
        assertThat(e.getStatus()).isEqualTo(EvaluationStatus.AWARDED);
    }

    @Test
    void skipsStagesThatHaveNothingToDo() {
        Evaluation noTechnical = evaluation();
        noTechnical.appoint(USER, NOW);
        noTechnical.closePreliminary(false, true, NOW);
        assertThat(noTechnical.getStatus()).isEqualTo(EvaluationStatus.FINANCIAL);
        assertThat(noTechnical.technicalDone()).isTrue();

        Evaluation nobodyPassed = evaluation();
        nobodyPassed.appoint(USER, NOW);
        nobodyPassed.closePreliminary(true, false, NOW);
        assertThat(nobodyPassed.getStatus()).isEqualTo(EvaluationStatus.REPORT);
    }

    @Test
    void aNoAwardRecommendationCannotBeAwarded() {
        Evaluation e = evaluation();
        e.appoint(USER, NOW);
        e.closePreliminary(true, false, NOW);
        expectRefused(() -> e.submitReport(USER, null, null, false, "x", NOW));
        e.submitReport(USER, null, null, true, "No bid met the mandatory requirements", NOW);
        e.signed(NOW);
        expectRefused(() -> e.award(NOW));
        e.terminate(NOW);
        assertThat(e.getStatus()).isEqualTo(EvaluationStatus.TERMINATED);
    }

    @Test
    void membersDeclareOnceAndConflictsExcludeThem() {
        EvaluationMember m = new EvaluationMember(UUID.randomUUID(), USER, CommitteeRole.MEMBER, NOW);
        assertThat(m.mayScore(true)).isFalse();
        assertThat(m.mayScore(false)).isTrue();
        expectRefused(() -> m.declare(true, " ", NOW));
        m.declare(true, "Shareholder in a bidder", NOW);
        assertThat(m.mayScore(true)).isFalse();
        expectRefused(() -> m.declare(false, null, NOW));

        EvaluationMember sec = new EvaluationMember(UUID.randomUUID(), USER, CommitteeRole.SECRETARY, NOW);
        sec.declare(false, null, NOW);
        assertThat(sec.isCleared(true)).isTrue();
        assertThat(sec.mayScore(true)).isFalse();
    }

    @Test
    void opinionAndRefusalsNeedAWrittenNote() {
        EvaluationApproval opinion = new EvaluationApproval(UUID.randomUUID(), 1, 1, "HEAD_OF_PROCUREMENT", true);
        expectRefused(() -> opinion.decide(ApprovalStatus.APPROVED, USER, "", NOW));
        opinion.decide(ApprovalStatus.APPROVED, USER, "Concur: process followed the Act", NOW);
        expectRefused(() -> opinion.decide(ApprovalStatus.APPROVED, USER, "again", NOW));

        EvaluationApproval ao = new EvaluationApproval(UUID.randomUUID(), 1, 2, "ACCOUNTING_OFFICER", false);
        expectRefused(() -> ao.decide(ApprovalStatus.RETURNED, USER, null, NOW));
        ao.decide(ApprovalStatus.APPROVED, USER, null, NOW);
        assertThat(ao.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
    }
}
