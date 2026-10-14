package com.pmplatform.procurement.plan;

import com.pmplatform.platform.persistence.TenantEntity;
import com.pmplatform.platform.web.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One department's part of the plan, prepared by its head and submitted to procurement. */
@Entity
@Table(schema = "procurement", name = "plan_departments")
public class PlanDepartment extends TenantEntity {

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(nullable = false)
    private String name;

    @Column(name = "head_id", nullable = false)
    private UUID headId;

    @Column(name = "ceiling_minor")
    private Long ceilingMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DepartmentStatus status;

    @Column(name = "return_note")
    private String returnNote;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    protected PlanDepartment() {
    }

    public PlanDepartment(UUID tenantId, UUID planId, Instant now) {
        super(tenantId, now);
        this.planId = planId;
        this.status = DepartmentStatus.DRAFT;
    }

    public void define(String name, UUID headId, Long ceilingMinor, Instant now) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("Name the department");
        }
        if (name.trim().length() > 120) {
            throw new BusinessRuleException("The department name is at most 120 characters");
        }
        if (ceilingMinor != null && ceilingMinor < 0) {
            throw new BusinessRuleException("The budget ceiling cannot be negative");
        }
        this.name = name.trim();
        this.headId = headId;
        this.ceilingMinor = ceilingMinor;
        touch(now);
    }

    public boolean isDraft() {
        return status == DepartmentStatus.DRAFT;
    }

    public void submit(UUID by, long totalMinor, Instant now) {
        if (status != DepartmentStatus.DRAFT) {
            throw new BusinessRuleException(name + " has already been submitted");
        }
        if (ceilingMinor != null && totalMinor > ceilingMinor) {
            throw new BusinessRuleException(name + "'s lines come to more than its budget ceiling; reduce them "
                    + "or ask procurement to change the ceiling");
        }
        this.status = DepartmentStatus.SUBMITTED;
        this.submittedBy = by;
        this.submittedAt = now;
        this.returnNote = null;
        touch(now);
    }

    public void returnToHead(String note, Instant now) {
        if (status != DepartmentStatus.SUBMITTED) {
            throw new BusinessRuleException(name + " has not been submitted");
        }
        if (note == null || note.isBlank()) {
            throw new BusinessRuleException("Say what needs to change");
        }
        if (note.trim().length() > 2000) {
            throw new BusinessRuleException("The note is at most 2,000 characters");
        }
        this.status = DepartmentStatus.DRAFT;
        this.returnNote = note.trim();
        touch(now);
    }

    public boolean overCeiling(long totalMinor) {
        return ceilingMinor != null && totalMinor > ceilingMinor;
    }

    public UUID getPlanId() { return planId; }
    public String getName() { return name; }
    public UUID getHeadId() { return headId; }
    public Long getCeilingMinor() { return ceilingMinor; }
    public DepartmentStatus getStatus() { return status; }
    public String getReturnNote() { return returnNote; }
    public UUID getSubmittedBy() { return submittedBy; }
    public Instant getSubmittedAt() { return submittedAt; }
}
