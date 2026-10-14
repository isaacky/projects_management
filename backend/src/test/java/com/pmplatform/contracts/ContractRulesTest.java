package com.pmplatform.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.pmplatform.platform.web.BusinessRuleException;
import com.pmplatform.platform.web.ForbiddenActionException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContractRulesTest {

    static final Instant NOW = Instant.parse("2026-11-01T08:00:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 11, 1);
    static final UUID USER = UUID.randomUUID();
    static final UUID OTHER = UUID.randomUUID();

    static Contract contract(long value, Instant standstillEnds) {
        return new Contract(UUID.randomUUID(), "CON-2026-0001", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Transformers", "KES", value,
                standstillEnds, USER, NOW);
    }

    static void refused(Runnable r) {
        try {
            r.run();
        } catch (BusinessRuleException | ForbiddenActionException expected) {
            return;
        }
        throw new AssertionError("expected a refusal");
    }

    @Test
    void retentionRoundsToTheNearestCent() {
        assertThat(ContractRules.retention(1_000_000, 1000)).isEqualTo(100_000L);
        assertThat(ContractRules.retention(15, 1000)).isEqualTo(2L);   // 1.5 -> 2
        assertThat(ContractRules.retention(14, 1000)).isEqualTo(1L);
        assertThat(ContractRules.retention(1_000_000, 0)).isEqualTo(0L);
    }

    @Test
    void variationIncreasesAreCappedAtAQuarterOfTheOriginalPrice() {
        assertThat(ContractRules.variationCapProblem(1_000_000, 0, 250_000)).isNull();
        assertThat(ContractRules.variationCapProblem(1_000_000, 200_000, 50_000)).isNull();
        assertThat(ContractRules.variationCapProblem(1_000_000, 200_000, 50_001)).isNotNull();
        assertThat(ContractRules.variationCapProblem(1_000_000, 250_000, -100_000)).isNull(); // decreases are fine
    }

    @Test
    void invoicesCannotExceedTheContractOrTheMilestone() {
        UUID contractId = UUID.randomUUID();
        Milestone m = new Milestone(contractId, 1, "Delivery", null, null, 600_000);
        Invoice first = new Invoice(contractId, InvoiceKind.INVOICE, 1, "INV-1", TODAY, m.getId(), 500_000, 0, USER, NOW);
        assertThat(ContractRules.invoiceCapProblem(1_000_000, List.of(first), 500_000)).isNull();
        assertThat(ContractRules.invoiceCapProblem(1_000_000, List.of(first), 500_001)).isNotNull();
        assertThat(ContractRules.milestoneCapProblem(m, List.of(first), 100_000)).isNull();
        assertThat(ContractRules.milestoneCapProblem(m, List.of(first), 100_001)).isNotNull();
        first.reject("Wrong amount");
        assertThat(ContractRules.milestoneCapProblem(m, List.of(first), 600_000)).isNull();
    }

    @Test
    void signingNeedsStandstillScheduleSecurityAndDocument() {
        Contract c = contract(1_000_000, NOW.plus(Duration.ofDays(3)));
        List<String> problems = ContractRules.signingProblems(c, List.of(), List.of(), false, NOW);
        assertThat(problems).hasSize(5); // standstill, dates, manager, milestones, document

        c.editDraft("Transformers", TODAY, TODAY.plusDays(90), 1000, 180, 100_000, USER, NOW);
        Milestone a = new Milestone(c.getId(), 1, "Delivery", null, null, 700_000);
        Milestone b = new Milestone(c.getId(), 2, "Installation", null, null, 300_000);
        Security s = new Security(c.getId(), SecurityKind.PERFORMANCE, SecurityForm.BANK_GUARANTEE, 100_000,
                "Sample Bank", "PG/001", TODAY.plusDays(270), TODAY, USER);
        Instant later = NOW.plus(Duration.ofDays(4));
        assertThat(ContractRules.signingProblems(c, List.of(a, b), List.of(s), true, later)).isEmpty();
        assertThat(ContractRules.signingProblems(c, List.of(a), List.of(s), true, later)).hasSize(1);
        assertThat(ContractRules.signingProblems(c, List.of(a, b), List.of(), true, later)).hasSize(1);

        refused(() -> c.sign(TODAY, "Supplier MD", "Accounting Officer", USER, NOW)); // standstill
        c.recordReview(ReviewStatus.LODGED, "PPARB 12/2026", NOW);
        refused(() -> c.sign(TODAY, "Supplier MD", "Accounting Officer", USER, later));
        c.recordReview(ReviewStatus.DISMISSED, "PPARB 12/2026 dismissed", NOW);
        c.sign(TODAY, "Supplier MD", "Accounting Officer", USER, later);
        assertThat(c.getStatus()).isEqualTo(ContractStatus.ACTIVE);
    }

    @Test
    void anUpheldReviewCancelsTheDraft() {
        Contract c = contract(1_000, NOW);
        c.recordReview(ReviewStatus.UPHELD, "PPARB 13/2026: re-evaluate", NOW);
        assertThat(c.getStatus()).isEqualTo(ContractStatus.CANCELLED);
    }

    @Test
    void paymentCertificatesSeparateCertifierAndApprover() {
        Invoice i = new Invoice(UUID.randomUUID(), InvoiceKind.INVOICE, 1, "INV-9", TODAY, null, 1_000_000,
                ContractRules.retention(1_000_000, 1000), USER, NOW);
        refused(() -> i.approve(OTHER, NOW));                  // not certified yet
        refused(() -> i.certify(USER, " ", NOW));              // needs a note
        i.certify(USER, "Delivery note DN-4 checked", NOW);
        refused(() -> i.approve(USER, NOW));                   // same person
        i.approve(OTHER, NOW);
        refused(() -> i.pay(OTHER, TODAY, ""));
        i.pay(OTHER, TODAY, "EFT-778");
        assertThat(i.netMinor()).isEqualTo(900_000L);
        refused(() -> i.reject("late"));

        Invoice release = new Invoice(i.getContractId(), InvoiceKind.RETENTION_RELEASE, 2, null, TODAY, null,
                40_000, 0, USER, NOW);
        release.certify(USER, "Defects period over", NOW);
        release.approve(OTHER, NOW);
        release.pay(OTHER, TODAY, "EFT-900");
        ContractRules.Totals t = ContractRules.totals(List.of(i, release));
        assertThat(t.invoiced()).isEqualTo(1_000_000L);
        assertThat(t.certified()).isEqualTo(1_000_000L);
        assertThat(t.paid()).isEqualTo(940_000L);
        assertThat(t.retentionHeld()).isEqualTo(60_000L);
    }

    @Test
    void variationsAndCompletion() {
        Contract c = contract(1_000_000, NOW);
        c.editDraft("Transformers", TODAY, TODAY.plusDays(90), 0, 0, 0, USER, NOW);
        c.sign(TODAY, "A", "B", USER, NOW);
        c.applyVariation(200_000, TODAY.plusDays(120), NOW);
        assertThat(c.getCurrentValueMinor()).isEqualTo(1_200_000L);
        assertThat(c.getEndDate()).isEqualTo(TODAY.plusDays(120));
        assertThat(c.getOriginalEndDate()).isEqualTo(TODAY.plusDays(90));
        refused(() -> c.complete(TODAY, 6, 4, 4, null, USER, NOW));
        c.complete(TODAY.plusDays(100), 4, 3, 5, "Good work, slight delay", USER, NOW);
        assertThat(c.defectsLiabilityEnds()).isEqualTo(TODAY.plusDays(100));
        refused(() -> c.terminate("x", NOW));
    }

    @Test
    void securitiesExtendAndClose() {
        Security s = new Security(UUID.randomUUID(), SecurityKind.PERFORMANCE, SecurityForm.BANK_GUARANTEE, 5,
                "Bank", "G1", TODAY.plusDays(30), TODAY, USER);
        refused(() -> s.extend(TODAY.plusDays(10)));
        s.markExpiryNotified(NOW);
        s.extend(TODAY.plusDays(60));
        assertThat(s.getExpiryNotifiedAt()).isNull();
        refused(() -> s.close(SecurityStatus.RELEASED, ""));
        s.close(SecurityStatus.RELEASED, "Contract completed");
        refused(() -> s.extend(TODAY.plusDays(90)));
    }
}
