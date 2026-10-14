package com.pmplatform.procurement.config;

import com.pmplatform.platform.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * How evaluation committees work: size, quorum, whether technical and financial bids
 * are opened separately, and who signs, reviews and approves. A rule with no method
 * code is the tenant's default; a method-specific rule overrides it.
 */
@Entity
@Table(schema = "procurement", name = "evaluation_committee_rules")
public class EvaluationCommitteeRule extends TenantEntity {

    @Column(name = "method_code", updatable = false)
    private String methodCode;

    @Column(name = "min_members", nullable = false)
    private int minMembers;

    @Column(name = "max_members", nullable = false)
    private int maxMembers;

    @Column(nullable = false)
    private int quorum;

    @Column(name = "two_envelope", nullable = false)
    private boolean twoEnvelope;

    @Column(name = "technical_pass_mark_pct")
    private Integer technicalPassMarkPct;

    @Column(name = "requires_coi_declaration", nullable = false)
    private boolean requiresCoiDeclaration;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_signatories", nullable = false)
    private ReportSignatories reportSignatories;

    @Column(name = "report_reviewer_role")
    private String reportReviewerRole;

    @Column(name = "award_approver_role", nullable = false)
    private String awardApproverRole;

    protected EvaluationCommitteeRule() {
    }

    public EvaluationCommitteeRule(UUID tenantId, String methodCode, Instant now) {
        super(tenantId, now);
        this.methodCode = methodCode;
    }

    public void update(int minMembers, int maxMembers, int quorum, boolean twoEnvelope,
            Integer technicalPassMarkPct, boolean requiresCoiDeclaration,
            ReportSignatories reportSignatories, String reportReviewerRole,
            String awardApproverRole, Instant now) {
        this.minMembers = minMembers;
        this.maxMembers = maxMembers;
        this.quorum = quorum;
        this.twoEnvelope = twoEnvelope;
        this.technicalPassMarkPct = technicalPassMarkPct;
        this.requiresCoiDeclaration = requiresCoiDeclaration;
        this.reportSignatories = reportSignatories;
        this.reportReviewerRole = reportReviewerRole;
        this.awardApproverRole = awardApproverRole;
        touch(now);
    }

    public boolean isDefault() {
        return methodCode == null;
    }

    public String getMethodCode() { return methodCode; }
    public int getMinMembers() { return minMembers; }
    public int getMaxMembers() { return maxMembers; }
    public int getQuorum() { return quorum; }
    public boolean isTwoEnvelope() { return twoEnvelope; }
    public Integer getTechnicalPassMarkPct() { return technicalPassMarkPct; }
    public boolean isRequiresCoiDeclaration() { return requiresCoiDeclaration; }
    public ReportSignatories getReportSignatories() { return reportSignatories; }
    public String getReportReviewerRole() { return reportReviewerRole; }
    public String getAwardApproverRole() { return awardApproverRole; }
}
