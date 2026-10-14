package com.pmplatform.procurement.plan;

import com.pmplatform.platform.persistence.TenantEntity;
import com.pmplatform.platform.web.BusinessRuleException;
import com.pmplatform.procurement.tender.ReservedGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One planned purchase. Requisitions are measured against its approved estimate. */
@Entity
@Table(schema = "procurement", name = "plan_lines")
public class PlanLine extends TenantEntity {

    /** What is entered for a line, by hand or from a CSV file. */
    public record LineInput(String description, PlanCategory category, BigDecimal quantity, String unit,
            long estimateMinor, String methodCode, int quarter, String sourceOfFunds, String budgetCode,
            ReservedGroup reservedGroup, UUID projectId) {
    }

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "line_no", nullable = false, updatable = false)
    private int lineNo;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanCategory category;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false)
    private String unit;

    @Column(name = "estimate_minor", nullable = false)
    private long estimateMinor;

    @Column(name = "method_code", nullable = false)
    private String methodCode;

    @Column(nullable = false)
    private int quarter;

    @Column(name = "source_of_funds")
    private String sourceOfFunds;

    @Column(name = "budget_code")
    private String budgetCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "reserved_group")
    private ReservedGroup reservedGroup;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "approved_estimate_minor")
    private Long approvedEstimateMinor;

    @Column(name = "approved_revision")
    private Integer approvedRevision;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_note")
    private String cancelNote;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PlanLine() {
    }

    public PlanLine(UUID tenantId, UUID planId, UUID departmentId, int lineNo, UUID createdBy, Instant now) {
        super(tenantId, now);
        this.planId = planId;
        this.departmentId = departmentId;
        this.lineNo = lineNo;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public void define(LineInput in, Instant now) {
        requireActive();
        String desc = trim(in.description());
        if (desc == null) {
            throw new BusinessRuleException("Describe what is to be bought");
        }
        if (desc.length() > 500) {
            throw new BusinessRuleException("The description is at most 500 characters");
        }
        if (in.category() == null) {
            throw new BusinessRuleException("Choose the category (goods, works, non-consulting services or consultancy)");
        }
        if (in.quantity() == null || in.quantity().signum() <= 0) {
            throw new BusinessRuleException("The quantity must be more than zero");
        }
        if (in.quantity().scale() > 3 || in.quantity().precision() - in.quantity().scale() > 11) {
            throw new BusinessRuleException("The quantity has too many digits");
        }
        String u = trim(in.unit());
        if (u == null || u.length() > 32) {
            throw new BusinessRuleException("Give the unit (at most 32 characters), e.g. lot, km, each");
        }
        if (in.estimateMinor() < 0) {
            throw new BusinessRuleException("The estimate cannot be negative");
        }
        if (in.quarter() < 1 || in.quarter() > 4) {
            throw new BusinessRuleException("The quarter is 1 to 4");
        }
        if (in.methodCode() == null || in.methodCode().isBlank()) {
            throw new BusinessRuleException("Choose the procurement method");
        }
        this.description = desc;
        this.category = in.category();
        this.quantity = in.quantity();
        this.unit = u;
        this.estimateMinor = in.estimateMinor();
        this.methodCode = in.methodCode().trim();
        this.quarter = in.quarter();
        this.sourceOfFunds = limited(in.sourceOfFunds(), 120, "The source of funds");
        this.budgetCode = limited(in.budgetCode(), 60, "The budget code");
        this.reservedGroup = in.reservedGroup();
        this.projectId = in.projectId();
        touch(now);
    }

    public void moveTo(UUID departmentId, Instant now) {
        this.departmentId = departmentId;
        touch(now);
    }

    public void approved(int revision, Instant now) {
        if (isCancelled()) {
            return;
        }
        if (approvedEstimateMinor == null || approvedEstimateMinor != estimateMinor) {
            this.approvedRevision = revision;
        }
        this.approvedEstimateMinor = estimateMinor;
        touch(now);
    }

    public void cancel(String note, Instant now) {
        requireActive();
        if (note == null || note.isBlank()) {
            throw new BusinessRuleException("Give the reason for dropping this line");
        }
        if (note.trim().length() > 2000) {
            throw new BusinessRuleException("The reason is at most 2,000 characters");
        }
        this.cancelledAt = now;
        this.cancelNote = note.trim();
        touch(now);
    }

    public boolean isCancelled() {
        return cancelledAt != null;
    }

    /** Usable by requisitions: approved at least once and not dropped. */
    public boolean isUsable() {
        return !isCancelled() && approvedEstimateMinor != null;
    }

    /** "NEW", "CHANGED" or null, compared with the last approval. */
    public String change() {
        if (isCancelled()) {
            return null;
        }
        if (approvedEstimateMinor == null) {
            return "NEW";
        }
        return approvedEstimateMinor != estimateMinor ? "CHANGED" : null;
    }

    private void requireActive() {
        if (isCancelled()) {
            throw new BusinessRuleException("Line " + lineNo + " was dropped from the plan");
        }
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String limited(String s, int max, String what) {
        String t = trim(s);
        if (t != null && t.length() > max) {
            throw new BusinessRuleException(what + " is at most " + max + " characters");
        }
        return t;
    }

    public UUID getPlanId() { return planId; }
    public UUID getDepartmentId() { return departmentId; }
    public int getLineNo() { return lineNo; }
    public String getDescription() { return description; }
    public PlanCategory getCategory() { return category; }
    public BigDecimal getQuantity() { return quantity; }
    public String getUnit() { return unit; }
    public long getEstimateMinor() { return estimateMinor; }
    public String getMethodCode() { return methodCode; }
    public int getQuarter() { return quarter; }
    public String getSourceOfFunds() { return sourceOfFunds; }
    public String getBudgetCode() { return budgetCode; }
    public ReservedGroup getReservedGroup() { return reservedGroup; }
    public UUID getProjectId() { return projectId; }
    public Long getApprovedEstimateMinor() { return approvedEstimateMinor; }
    public Integer getApprovedRevision() { return approvedRevision; }
    public Instant getCancelledAt() { return cancelledAt; }
    public String getCancelNote() { return cancelNote; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }

}
