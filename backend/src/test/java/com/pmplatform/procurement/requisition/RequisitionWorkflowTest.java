package com.pmplatform.procurement.requisition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pmplatform.platform.web.BusinessRuleException;
import com.pmplatform.platform.web.ForbiddenActionException;
import com.pmplatform.procurement.requisition.Requisition.ChainStep;
import com.pmplatform.procurement.requisition.Requisition.LineInput;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequisitionWorkflowTest {

    private static final Instant NOW = Instant.parse("2026-10-10T08:00:00Z");
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID REQUESTER = UUID.randomUUID();
    private static final UUID HOD = UUID.randomUUID();
    private static final UUID AO = UUID.randomUUID();
    private static final Set<String> HOD_ROLES = Set.of("HEAD_OF_DEPARTMENT");
    private static final Set<String> AO_ROLES = Set.of("ACCOUNTING_OFFICER");

    private static Requisition draft() {
        Requisition r = new Requisition(TENANT, "REQ-2026-0001", REQUESTER, NOW);
        r.edit(REQUESTER, UUID.randomUUID(), "Transformers", null, "Networks", null, "KES", null,
                List.of(new LineInput("Transformer 200kVA", new BigDecimal("2"), "each", 150_000_000L),
                        new LineInput("Cable", new BigDecimal("12.5"), "m", 3_333L)), NOW);
        return r;
    }

    private static List<ChainStep> chain() {
        return List.of(new ChainStep(2, "ACCOUNTING_OFFICER"), new ChainStep(1, "HEAD_OF_DEPARTMENT"));
    }

    @Test
    void totalsAreRoundedPerLineToTheCent() {
        // 12.5 × 3,333 cents = 41,662.5 → 41,663
        assertThat(draft().getEstimatedTotalMinor()).isEqualTo(300_000_000L + 41_663L);
    }

    @Test
    void approvesThroughTheChainInStepOrder() {
        Requisition r = draft();
        assertThat(r.submit(REQUESTER, chain(), NOW).getApproverRole()).isEqualTo("HEAD_OF_DEPARTMENT");
        assertThat(r.getStatus()).isEqualTo(RequisitionStatus.SUBMITTED);

        var first = r.approve(HOD, HOD_ROLES, "ok", NOW);
        assertThat(first.nextStep()).get().extracting(RequisitionApproval::getApproverRole)
                .isEqualTo("ACCOUNTING_OFFICER");
        assertThat(r.getStatus()).isEqualTo(RequisitionStatus.SUBMITTED);

        var last = r.approve(AO, AO_ROLES, null, NOW);
        assertThat(last.nextStep()).isEmpty();
        assertThat(r.getStatus()).isEqualTo(RequisitionStatus.APPROVED);
    }

    @Test
    void enforcesRolesAndSegregationOfDuties() {
        Requisition r = draft();
        r.submit(REQUESTER, chain(), NOW);
        assertThatThrownBy(() -> r.approve(AO, AO_ROLES, null, NOW))
                .isInstanceOf(ForbiddenActionException.class).hasMessageContaining("HEAD_OF_DEPARTMENT");
        assertThatThrownBy(() -> r.approve(REQUESTER, HOD_ROLES, null, NOW))
                .isInstanceOf(ForbiddenActionException.class).hasMessageContaining("your own");
        assertThatThrownBy(() -> r.submit(HOD, chain(), NOW)).isInstanceOf(ForbiddenActionException.class);
    }

    @Test
    void refusesIncompleteSubmissions() {
        Requisition empty = new Requisition(TENANT, "REQ-2", REQUESTER, NOW);
        empty.edit(REQUESTER, UUID.randomUUID(), "x", null, null, null, "KES", null, List.of(), NOW);
        assertThatThrownBy(() -> empty.submit(REQUESTER, chain(), NOW)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> draft().submit(REQUESTER, List.of(), NOW)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void returnedRequisitionsCanBeEditedAndResubmittedAsANewRound() {
        Requisition r = draft();
        r.submit(REQUESTER, chain(), NOW);
        assertThatThrownBy(() -> r.returnForChanges(HOD, HOD_ROLES, " ", NOW))
                .isInstanceOf(BusinessRuleException.class);
        r.returnForChanges(HOD, HOD_ROLES, "Split the cable line", NOW);
        assertThat(r.getStatus()).isEqualTo(RequisitionStatus.RETURNED);

        r.edit(REQUESTER, r.getProjectId(), "Transformers v2", null, null, null, "KES", null,
                List.of(new LineInput("Transformer", BigDecimal.ONE, "each", 100L)), NOW);
        r.submit(REQUESTER, chain(), NOW);
        assertThat(r.getCurrentRound()).isEqualTo(2);
        assertThat(r.getApprovals()).hasSize(4);
        assertThat(r.pendingStep()).get().extracting(RequisitionApproval::getRound).isEqualTo(2);
    }

    @Test
    void rejectionAndWithdrawalAreFinal() {
        Requisition rejected = draft();
        rejected.submit(REQUESTER, chain(), NOW);
        rejected.reject(HOD, HOD_ROLES, "Not budgeted", NOW);
        assertThat(rejected.getStatus()).isEqualTo(RequisitionStatus.REJECTED);
        assertThatThrownBy(() -> rejected.submit(REQUESTER, chain(), NOW)).isInstanceOf(BusinessRuleException.class);

        Requisition withdrawn = draft();
        withdrawn.submit(REQUESTER, chain(), NOW);
        assertThat(withdrawn.cancel(REQUESTER, NOW)).get()
                .extracting(RequisitionApproval::getApproverRole).isEqualTo("HEAD_OF_DEPARTMENT");
        assertThat(withdrawn.getStatus()).isEqualTo(RequisitionStatus.CANCELLED);
        assertThat(withdrawn.currentRoundSteps())
                .allMatch(s -> s.getStatus() == ApprovalStepStatus.CANCELLED);
    }

    @Test
    void refusesZeroQuantities() {
        Requisition r = new Requisition(TENANT, "REQ-3", REQUESTER, NOW);
        assertThatThrownBy(() -> r.edit(REQUESTER, UUID.randomUUID(), "x", null, null, null, "KES", null,
                List.of(new LineInput("bad", BigDecimal.ZERO, "each", 1L)), NOW))
                .isInstanceOf(BusinessRuleException.class);
    }
}
