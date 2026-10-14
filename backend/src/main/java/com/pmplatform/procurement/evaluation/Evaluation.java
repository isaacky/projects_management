package com.pmplatform.procurement.evaluation;

import com.pmplatform.platform.persistence.TenantEntity;
import com.pmplatform.platform.web.BusinessRuleException;
import com.pmplatform.procurement.config.EvaluationCommitteeRule;
import com.pmplatform.procurement.config.ReportSignatories;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * The evaluation of one tender's bids by an appointed committee, from setup to the approved
 * award. The committee rule in force at setup is copied here so later settings changes do not
 * alter an evaluation under way. See {@link EvaluationStatus} for the flow.
 */
@Entity
@Table(schema = "procurement", name = "evaluations")
public class Evaluation extends TenantEntity {

    @Column(name = "tender_id", nullable = false, updatable = false)
    private UUID tenderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvaluationStatus status;

    @Column(name = "template_code")
    private String templateCode;

    @Column(name = "technical_pass_mark_pct")
    private Integer technicalPassMarkPct;

    @Column(name = "min_members", nullable = false, updatable = false)
    private int minMembers;

    @Column(name = "max_members", nullable = false, updatable = false)
    private int maxMembers;

    @Column(nullable = false, updatable = false)
    private int quorum;

    @Column(name = "requires_coi_declaration", nullable = false, updatable = false)
    private boolean requiresCoiDeclaration;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_signatories", nullable = false, updatable = false)
    private ReportSignatories reportSignatories;

    @Column(name = "report_reviewer_role", updatable = false)
    private String reportReviewerRole;

    @Column(name = "award_approver_role", nullable = false, updatable = false)
    private String awardApproverRole;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "appointed_by")
    private UUID appointedBy;

    @Column(name = "appointed_at")
    private Instant appointedAt;

    @Column(name = "preliminary_closed_at")
    private Instant preliminaryClosedAt;

    @Column(name = "technical_closed_at")
    private Instant technicalClosedAt;

    @Column(name = "financial_closed_at")
    private Instant financialClosedAt;

    @Column(name = "round", nullable = false)
    private int reportRound = 1;

    @Column(name = "no_award", nullable = false)
    private boolean noAward;

    @Column(name = "recommended_bid_id")
    private UUID recommendedBidId;

    @Column(name = "recommended_amount_minor")
    private Long recommendedAmountMinor;

    private String recommendation;

    @Column(name = "report_submitted_by")
    private UUID reportSubmittedBy;

    @Column(name = "report_submitted_at")
    private Instant reportSubmittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected Evaluation() {
    }

    public Evaluation(UUID tenantId, UUID tenderId, EvaluationCommitteeRule rule, String templateCode,
            UUID createdBy, Instant now) {
        super(tenantId, now);
        this.tenderId = tenderId;
        this.status = EvaluationStatus.SETUP;
        this.templateCode = templateCode;
        this.technicalPassMarkPct = rule.getTechnicalPassMarkPct();
        this.minMembers = rule.getMinMembers();
        this.maxMembers = rule.getMaxMembers();
        this.quorum = rule.getQuorum();
        this.requiresCoiDeclaration = rule.isRequiresCoiDeclaration();
        this.reportSignatories = rule.getReportSignatories();
        this.reportReviewerRole = rule.getReportReviewerRole();
        this.awardApproverRole = rule.getAwardApproverRole();
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    /** Marks the evaluation as changed (for example its committee), so concurrent edits are detected. */
    public void changed(Instant now) {
        touch(now);
    }

    // ------------------------------------------------------------------ setup

    public void useTemplate(String code, Instant now) {
        requireStatus(EvaluationStatus.SETUP, "change its criteria");
        this.templateCode = code;
        touch(now);
    }

    public void setPassMark(Integer pct, Instant now) {
        requireStatus(EvaluationStatus.SETUP, "change the pass mark");
        if (pct != null && (pct < 0 || pct > 100)) {
            throw new BusinessRuleException("The pass mark is a percentage from 0 to 100");
        }
        this.technicalPassMarkPct = pct;
        touch(now);
    }

    /** The committee is appointed and the evaluation starts. Checks on members and criteria are done first. */
    public void appoint(UUID by, Instant now) {
        requireStatus(EvaluationStatus.SETUP, "be appointed");
        status = EvaluationStatus.PRELIMINARY;
        appointedBy = by;
        appointedAt = now;
        touch(now);
    }

    // ------------------------------------------------------------------ stages

    /**
     * @param technicalCriteria whether there is a technical stage at all
     * @param anyPassed whether at least one bid passed
     */
    public void closePreliminary(boolean technicalCriteria, boolean anyPassed, Instant now) {
        requireStatus(EvaluationStatus.PRELIMINARY, "close the preliminary stage");
        preliminaryClosedAt = now;
        if (!technicalCriteria) {
            technicalClosedAt = now;
        }
        status = !anyPassed ? EvaluationStatus.REPORT
                : technicalCriteria ? EvaluationStatus.TECHNICAL : EvaluationStatus.FINANCIAL;
        touch(now);
    }

    public void closeTechnical(boolean anyPassed, Instant now) {
        requireStatus(EvaluationStatus.TECHNICAL, "close the technical stage");
        technicalClosedAt = now;
        status = anyPassed ? EvaluationStatus.FINANCIAL : EvaluationStatus.REPORT;
        touch(now);
    }

    public void closeFinancial(Instant now) {
        requireStatus(EvaluationStatus.FINANCIAL, "close the financial stage");
        financialClosedAt = now;
        status = EvaluationStatus.REPORT;
        touch(now);
    }

    /** Technical evaluation is over (or there was none): financial envelopes may be opened. */
    public boolean technicalDone() {
        return technicalClosedAt != null;
    }

    // ------------------------------------------------------------------ report and approval

    public void submitReport(UUID by, UUID bidId, Long amountMinor, boolean noAward, String text, Instant now) {
        requireStatus(EvaluationStatus.REPORT, "take a report");
        if (text == null || text.isBlank()) {
            throw new BusinessRuleException("Write the committee's recommendation");
        }
        if (!noAward && (bidId == null || amountMinor == null)) {
            throw new BusinessRuleException("Choose the bid recommended for award, or recommend no award");
        }
        this.noAward = noAward;
        this.recommendedBidId = noAward ? null : bidId;
        this.recommendedAmountMinor = noAward ? null : amountMinor;
        this.recommendation = text.trim();
        this.reportSubmittedBy = by;
        this.reportSubmittedAt = now;
        this.status = EvaluationStatus.SIGNING;
        touch(now);
    }

    public void signed(Instant now) {
        requireStatus(EvaluationStatus.SIGNING, "move to approval");
        status = EvaluationStatus.APPROVAL;
        touch(now);
    }

    /** An approver sent the report back: the committee revises it and every signature is needed again. */
    public void returnToCommittee(Instant now) {
        requireStatus(EvaluationStatus.APPROVAL, "be returned");
        status = EvaluationStatus.REPORT;
        reportRound++;
        touch(now);
    }

    public void award(Instant now) {
        requireStatus(EvaluationStatus.APPROVAL, "be awarded");
        if (noAward) {
            throw new BusinessRuleException("The committee recommended no award");
        }
        status = EvaluationStatus.AWARDED;
        decidedAt = now;
        touch(now);
    }

    public void terminate(Instant now) {
        requireStatus(EvaluationStatus.APPROVAL, "be terminated");
        status = EvaluationStatus.TERMINATED;
        decidedAt = now;
        touch(now);
    }

    public void requireStatus(EvaluationStatus expected, String verb) {
        if (status != expected) {
            throw new BusinessRuleException("The evaluation is at the " + label(status) + " stage and cannot " + verb);
        }
    }

    static String label(EvaluationStatus s) {
        return s.name().toLowerCase(Locale.ROOT);
    }

    public UUID getTenderId() { return tenderId; }
    public EvaluationStatus getStatus() { return status; }
    public String getTemplateCode() { return templateCode; }
    public Integer getTechnicalPassMarkPct() { return technicalPassMarkPct; }
    public int getMinMembers() { return minMembers; }
    public int getMaxMembers() { return maxMembers; }
    public int getQuorum() { return quorum; }
    public boolean isRequiresCoiDeclaration() { return requiresCoiDeclaration; }
    public ReportSignatories getReportSignatories() { return reportSignatories; }
    public String getReportReviewerRole() { return reportReviewerRole; }
    public String getAwardApproverRole() { return awardApproverRole; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getAppointedBy() { return appointedBy; }
    public Instant getAppointedAt() { return appointedAt; }
    public Instant getPreliminaryClosedAt() { return preliminaryClosedAt; }
    public Instant getTechnicalClosedAt() { return technicalClosedAt; }
    public Instant getFinancialClosedAt() { return financialClosedAt; }
    public int getRound() { return reportRound; }
    public boolean isNoAward() { return noAward; }
    public UUID getRecommendedBidId() { return recommendedBidId; }
    public Long getRecommendedAmountMinor() { return recommendedAmountMinor; }
    public String getRecommendation() { return recommendation; }
    public UUID getReportSubmittedBy() { return reportSubmittedBy; }
    public Instant getReportSubmittedAt() { return reportSubmittedAt; }
    public Instant getDecidedAt() { return decidedAt; }
}
