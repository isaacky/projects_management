package com.pmplatform.procurement.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.pmplatform.procurement.config.ProcurementRules.ApprovalStep;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProcurementRulesTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");
    private static final long KES_1M = 1_000_000_00L;

    private static ProcurementMethod method(String code, boolean enabled, Long min, Long max, int sort) {
        ProcurementMethod m = new ProcurementMethod(TENANT, code, NOW);
        m.update(code, null, enabled, min, max, "KES", 0, false, false, sort, NOW);
        return m;
    }

    private static ApprovalThreshold band(String method, long min, Long max, int step, String role) {
        ApprovalThreshold t = new ApprovalThreshold(TENANT, NOW);
        t.update(ApprovalStage.AWARD, method, min, max, "KES", step, role, true, NOW);
        return t;
    }

    private static EvaluationCommitteeRule committee(String method, int min) {
        EvaluationCommitteeRule r = new EvaluationCommitteeRule(TENANT, method, NOW);
        r.update(min, min + 2, min, false, null, true, ReportSignatories.ALL_MEMBERS, null,
                "ACCOUNTING_OFFICER", NOW);
        return r;
    }

    // ---------------------------------------------------------------- methods

    @Test
    void allowedMethodsRespectValueRangeAndEnabledFlag() {
        List<ProcurementMethod> all = List.of(
                method("OPEN_TENDER", true, null, null, 10),
                method("RFQ", true, null, 3 * KES_1M, 30),
                method("DIRECT", false, null, null, 40));

        assertThat(ProcurementRules.allowedMethods(all, 2 * KES_1M, "KES"))
                .extracting(ProcurementMethod::getCode).containsExactly("OPEN_TENDER", "RFQ");
        assertThat(ProcurementRules.allowedMethods(all, 3 * KES_1M, "KES"))
                .extracting(ProcurementMethod::getCode).containsExactly("OPEN_TENDER", "RFQ");
        assertThat(ProcurementRules.allowedMethods(all, 3 * KES_1M + 1, "KES"))
                .extracting(ProcurementMethod::getCode).containsExactly("OPEN_TENDER");
        assertThat(ProcurementRules.allowedMethods(all, 100, "USD")).isEmpty();
    }

    // ---------------------------------------------------------------- approvers

    @Test
    void approvalChainIsOrderedBySteps() {
        List<ApprovalThreshold> rows = List.of(
                band(null, 0, null, 2, "ACCOUNTING_OFFICER"),
                band(null, 0, null, 1, "HEAD_OF_PROCUREMENT"));
        assertThat(ProcurementRules.approvers(rows, "OPEN_TENDER", 10 * KES_1M, "KES"))
                .containsExactly(new ApprovalStep(1, "HEAD_OF_PROCUREMENT"),
                        new ApprovalStep(2, "ACCOUNTING_OFFICER"));
    }

    @Test
    void bandsIncludeTheirMinimumAndExcludeTheirMaximum() {
        List<ApprovalThreshold> rows = List.of(
                band(null, 0, 5 * KES_1M, 1, "HEAD_OF_DEPARTMENT"),
                band(null, 5 * KES_1M, null, 1, "ACCOUNTING_OFFICER"));
        assertThat(ProcurementRules.approvers(rows, null, 5 * KES_1M - 1, "KES"))
                .extracting(ApprovalStep::approverRole).containsExactly("HEAD_OF_DEPARTMENT");
        assertThat(ProcurementRules.approvers(rows, null, 5 * KES_1M, "KES"))
                .extracting(ApprovalStep::approverRole).containsExactly("ACCOUNTING_OFFICER");
    }

    @Test
    void methodSpecificBandsOverrideGeneralBands() {
        List<ApprovalThreshold> rows = List.of(
                band(null, 0, null, 1, "HEAD_OF_PROCUREMENT"),
                band(null, 0, null, 2, "ACCOUNTING_OFFICER"),
                band("RFQ", 0, 3 * KES_1M, 1, "HEAD_OF_PROCUREMENT"));

        assertThat(ProcurementRules.approvers(rows, "RFQ", KES_1M, "KES"))
                .extracting(ApprovalStep::approverRole).containsExactly("HEAD_OF_PROCUREMENT");
        // Above the RFQ band, the general chain applies again.
        assertThat(ProcurementRules.approvers(rows, "RFQ", 4 * KES_1M, "KES"))
                .extracting(ApprovalStep::approverRole)
                .containsExactly("HEAD_OF_PROCUREMENT", "ACCOUNTING_OFFICER");
    }

    @Test
    void inactiveBandsAreIgnored() {
        ApprovalThreshold off = band(null, 0, null, 1, "HEAD_OF_DEPARTMENT");
        off.update(ApprovalStage.AWARD, null, 0, null, "KES", 1, "HEAD_OF_DEPARTMENT", false, NOW);
        assertThat(ProcurementRules.approvers(List.of(off), null, KES_1M, "KES")).isEmpty();
    }

    @Test
    void detectsOverlappingBandsForTheSameStep() {
        ApprovalThreshold existing = band(null, 0, 5 * KES_1M, 1, "HEAD_OF_DEPARTMENT");
        List<ApprovalThreshold> rows = List.of(existing);

        assertThat(ProcurementRules.conflictingBand(rows, null, null, "KES", 1, 4 * KES_1M, null))
                .contains(existing);
        // Adjacent band, different step, different method or the row itself: no conflict.
        assertThat(ProcurementRules.conflictingBand(rows, null, null, "KES", 1, 5 * KES_1M, null)).isEmpty();
        assertThat(ProcurementRules.conflictingBand(rows, null, null, "KES", 2, 0, null)).isEmpty();
        assertThat(ProcurementRules.conflictingBand(rows, null, "RFQ", "KES", 1, 0, null)).isEmpty();
        assertThat(ProcurementRules.conflictingBand(rows, existing.getId(), null, "KES", 1, 0, null)).isEmpty();
    }

    // ---------------------------------------------------------------- committees

    @Test
    void committeeRuleFallsBackToTheDefault() {
        EvaluationCommitteeRule def = committee(null, 3);
        EvaluationCommitteeRule open = committee("OPEN_TENDER", 5);
        List<EvaluationCommitteeRule> rules = List.of(def, open);

        assertThat(ProcurementRules.committeeRule(rules, "OPEN_TENDER")).contains(open);
        assertThat(ProcurementRules.committeeRule(rules, "RFQ")).contains(def);
        assertThat(ProcurementRules.committeeRule(rules, null)).contains(def);
        assertThat(ProcurementRules.committeeRule(List.of(open), "RFQ")).isEmpty();
    }
}
