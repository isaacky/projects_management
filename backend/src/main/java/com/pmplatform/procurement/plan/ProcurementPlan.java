package com.pmplatform.procurement.plan;

import com.pmplatform.platform.persistence.TenantEntity;
import com.pmplatform.platform.web.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** The annual procurement plan for one financial year. See {@link PlanStatus} for the workflow. */
@Entity
@Table(schema = "procurement", name = "plans")
public class ProcurementPlan extends TenantEntity {

    @Column(name = "financial_year", nullable = false, updatable = false)
    private String financialYear;

    @Column(name = "fy_start", nullable = false, updatable = false)
    private LocalDate fyStart;

    @Column(name = "fy_end", nullable = false, updatable = false)
    private LocalDate fyEnd;

    @Column(nullable = false, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanStatus status;

    @Column(nullable = false)
    private int revision;

    @Column(name = "next_line_no", nullable = false)
    private int nextLineNo;

    @Column(name = "agpo_target_pct", nullable = false)
    private BigDecimal agpoTargetPct;

    @Column(name = "revision_reason")
    private String revisionReason;

    @Column(name = "return_note")
    private String returnNote;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProcurementPlan() {
    }

    public ProcurementPlan(UUID tenantId, LocalDate fyStart, UUID createdBy, Instant now) {
        super(tenantId, now);
        if (fyStart.getDayOfMonth() != 1) {
            throw new BusinessRuleException("A financial year starts on the first day of a month");
        }
        this.fyStart = fyStart;
        this.fyEnd = PlanRules.fyEnd(fyStart);
        this.financialYear = PlanRules.label(fyStart);
        this.currency = "KES";
        this.status = PlanStatus.DRAFT;
        this.nextLineNo = 1;
        this.agpoTargetPct = BigDecimal.valueOf(30);
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public boolean isDraft() {
        return status == PlanStatus.DRAFT;
    }

    public void requireDraft(String action) {
        if (status != PlanStatus.DRAFT) {
            throw new BusinessRuleException("The " + financialYear + " plan is " + status.name().toLowerCase()
                    + "; " + action + " only while it is being prepared or revised");
        }
    }

    /** True once the accounting officer has approved the plan at least once. */
    public boolean everApproved() {
        return revision > 0;
    }

    public boolean covers(LocalDate date) {
        return PlanRules.covers(fyStart, date);
    }

    public int takeLineNo() {
        return nextLineNo++;
    }

    public void setAgpoTarget(BigDecimal pct, Instant now) {
        requireDraft("change the AGPO target");
        if (pct == null || pct.signum() < 0 || pct.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessRuleException("The AGPO target is a percentage from 0 to 100");
        }
        this.agpoTargetPct = pct;
        touch(now);
    }

    public void submit(UUID by, Instant now) {
        requireDraft("submit it");
        this.status = PlanStatus.SUBMITTED;
        this.submittedBy = by;
        this.submittedAt = now;
        this.returnNote = null;
        touch(now);
    }

    public void returnForChanges(String note, Instant now) {
        if (status != PlanStatus.SUBMITTED) {
            throw new BusinessRuleException("Only a submitted plan can be returned");
        }
        if (note == null || note.isBlank()) {
            throw new BusinessRuleException("Say what needs to change");
        }
        this.status = PlanStatus.DRAFT;
        this.returnNote = note.trim();
        touch(now);
    }

    /** Returns the new revision number. Read the revision reason first: approval clears it. */
    public int approve(Instant now) {
        if (status != PlanStatus.SUBMITTED) {
            throw new BusinessRuleException("Only a submitted plan can be approved");
        }
        this.status = PlanStatus.APPROVED;
        this.revision++;
        this.returnNote = null;
        this.revisionReason = null;
        touch(now);
        return revision;
    }

    public void reopen(String reason, Instant now) {
        if (status != PlanStatus.APPROVED) {
            throw new BusinessRuleException("Only an approved plan can be revised");
        }
        if (reason == null || reason.trim().length() < 10) {
            throw new BusinessRuleException("Give the reason for revising the plan");
        }
        this.status = PlanStatus.DRAFT;
        this.revisionReason = reason.trim();
        this.submittedBy = null;
        this.submittedAt = null;
        touch(now);
    }

    public String getFinancialYear() { return financialYear; }
    public LocalDate getFyStart() { return fyStart; }
    public LocalDate getFyEnd() { return fyEnd; }
    public String getCurrency() { return currency; }
    public PlanStatus getStatus() { return status; }
    public int getRevision() { return revision; }
    public BigDecimal getAgpoTargetPct() { return agpoTargetPct; }
    public String getRevisionReason() { return revisionReason; }
    public String getReturnNote() { return returnNote; }
    public UUID getSubmittedBy() { return submittedBy; }
    public Instant getSubmittedAt() { return submittedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
