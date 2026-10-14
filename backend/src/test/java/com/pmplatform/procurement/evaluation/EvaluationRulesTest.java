package com.pmplatform.procurement.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.pmplatform.procurement.config.ProcurementRules.ApprovalStep;
import com.pmplatform.procurement.config.ReportSignatories;
import com.pmplatform.procurement.evaluation.EvaluationRules.Mark;
import com.pmplatform.procurement.evaluation.EvaluationRules.Step;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvaluationRulesTest {

    static final Instant NOW = Instant.parse("2026-10-10T08:00:00Z");
    final UUID c1 = UUID.randomUUID();
    final UUID c2 = UUID.randomUUID();
    final UUID bid = UUID.randomUUID();
    final UUID chair = UUID.randomUUID();
    final UUID m2 = UUID.randomUUID();
    final UUID m3 = UUID.randomUUID();
    final UUID m4 = UUID.randomUUID();

    Mark pass(UUID c, UUID member, boolean p) {
        return new Mark(c, bid, member, p, null);
    }

    Mark score(UUID c, UUID member, String s) {
        return new Mark(c, bid, member, null, new BigDecimal(s));
    }

    @Test
    void onlyMembersWhoMarkedEverythingCount() {
        List<Mark> marks = List.of(pass(c1, chair, true), pass(c2, chair, true), pass(c1, m2, true));
        assertThat(EvaluationRules.completeMembers(List.of(chair, m2), List.of(c1, c2), List.of(bid), marks))
                .containsExactly(chair);
    }

    @Test
    void preliminaryMajorityDecidesAndChairBreaksTies() {
        Set<UUID> members = Set.of(chair, m2, m3, m4);
        List<Mark> marks = List.of(
                pass(c1, chair, true), pass(c1, m2, true), pass(c1, m3, false), pass(c1, m4, true),   // 3-1 met
                pass(c2, chair, true), pass(c2, m2, false), pass(c2, m3, true), pass(c2, m4, false));  // 2-2, chair yes
        var r = EvaluationRules.preliminary(List.of(c1, c2), bid, members, chair, marks);
        assertThat(r.passed()).isTrue();

        List<Mark> chairSaysNo = List.of(
                pass(c1, chair, false), pass(c1, m2, true), pass(c1, m3, true), pass(c1, m4, false));
        var r2 = EvaluationRules.preliminary(List.of(c1), bid, members, chair, chairSaysNo);
        assertThat(r2.passed()).isFalse();
        assertThat(r2.failedCriteria()).containsExactly(c1);
    }

    @Test
    void preliminaryTieWithoutChairFails() {
        Set<UUID> members = Set.of(m2, m3);
        var r = EvaluationRules.preliminary(List.of(c1), bid, members, chair,
                List.of(pass(c1, m2, true), pass(c1, m3, false)));
        assertThat(r.passed()).isFalse();
    }

    @Test
    void technicalAveragesMembersAndComparesWithPassMark() {
        Map<UUID, BigDecimal> max = new LinkedHashMap<>();
        max.put(c1, new BigDecimal("60"));
        max.put(c2, new BigDecimal("40"));
        Set<UUID> members = Set.of(chair, m2, m3);
        List<Mark> marks = List.of(
                score(c1, chair, "50"), score(c1, m2, "40"), score(c1, m3, "45"),   // avg 45.00
                score(c2, chair, "30"), score(c2, m2, "25"), score(c2, m3, "26"));  // avg 27.00
        var r = EvaluationRules.technical(max, bid, members, marks, 70);
        assertThat(r.total()).isEqualByComparingTo("72.00");
        assertThat(r.pct()).isEqualByComparingTo("72.00");
        assertThat(r.passed()).isTrue();
        assertThat(EvaluationRules.technical(max, bid, members, marks, 73).passed()).isFalse();
    }

    @Test
    void technicalRoundsEachAverageToTwoDecimals() {
        Map<UUID, BigDecimal> max = Map.of(c1, new BigDecimal("10"));
        var r = EvaluationRules.technical(max, bid, Set.of(chair, m2, m3),
                List.of(score(c1, chair, "7"), score(c1, m2, "7"), score(c1, m3, "8")), 70);
        assertThat(r.total()).isEqualByComparingTo("7.33");
        assertThat(r.pct()).isEqualByComparingTo("73.30");
    }

    @Test
    void lowestPriceRanksFirstAndTiesShareARank() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        Map<UUID, Integer> ranks = EvaluationRules.rank(Map.of(a, 500L, b, 300L, c, 300L));
        assertThat(ranks.get(b)).isEqualTo(1);
        assertThat(ranks.get(c)).isEqualTo(1);
        assertThat(ranks.get(a)).isEqualTo(3);
    }

    @Test
    void signatoriesFollowTheRuleAndSkipConflictedMembers() {
        UUID eval = UUID.randomUUID();
        EvaluationMember ch = new EvaluationMember(eval, chair, CommitteeRole.CHAIR, NOW);
        EvaluationMember a = new EvaluationMember(eval, m2, CommitteeRole.MEMBER, NOW);
        EvaluationMember conflicted = new EvaluationMember(eval, m3, CommitteeRole.MEMBER, NOW);
        EvaluationMember sec = new EvaluationMember(eval, m4, CommitteeRole.SECRETARY, NOW);
        ch.declare(false, null, NOW);
        a.declare(false, null, NOW);
        conflicted.declare(true, "Director of a bidder is my brother", NOW);
        sec.declare(false, null, NOW);
        List<EvaluationMember> all = List.of(ch, a, conflicted, sec);
        assertThat(EvaluationRules.signatories(ReportSignatories.ALL_MEMBERS, all, true)).containsExactly(chair, m2);
        assertThat(EvaluationRules.signatories(ReportSignatories.CHAIR_AND_SECRETARY, all, true))
                .containsExactly(chair, m4);
        assertThat(EvaluationRules.signatories(ReportSignatories.CHAIR_ONLY, all, true)).containsExactly(chair);
    }

    @Test
    void approvalChainMarksTheProfessionalOpinion() {
        List<Step> fromSettings = EvaluationRules.approvalChain(
                List.of(new ApprovalStep(2, "ACCOUNTING_OFFICER"), new ApprovalStep(1, "HEAD_OF_PROCUREMENT")),
                "HEAD_OF_PROCUREMENT", "ACCOUNTING_OFFICER");
        assertThat(fromSettings).containsExactly(new Step(1, "HEAD_OF_PROCUREMENT", true),
                new Step(2, "ACCOUNTING_OFFICER", false));

        List<Step> noChain = EvaluationRules.approvalChain(List.of(), "HEAD_OF_PROCUREMENT", "ACCOUNTING_OFFICER");
        assertThat(noChain).containsExactly(new Step(1, "HEAD_OF_PROCUREMENT", true),
                new Step(2, "ACCOUNTING_OFFICER", false));

        List<Step> noReviewer = EvaluationRules.approvalChain(List.of(), null, "ACCOUNTING_OFFICER");
        assertThat(noReviewer).containsExactly(new Step(1, "ACCOUNTING_OFFICER", false));
    }
}
