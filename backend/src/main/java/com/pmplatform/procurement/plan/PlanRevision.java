package com.pmplatform.procurement.plan;

import com.pmplatform.platform.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One approval of the plan by the accounting officer. */
@Entity
@Table(schema = "procurement", name = "plan_revisions")
public class PlanRevision extends TenantEntity {

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(nullable = false, updatable = false)
    private int revision;

    @Column(name = "approved_by", nullable = false, updatable = false)
    private UUID approvedBy;

    @Column(name = "approved_at", nullable = false, updatable = false)
    private Instant approvedAt;

    @Column(name = "total_minor", nullable = false, updatable = false)
    private long totalMinor;

    @Column(name = "line_count", nullable = false, updatable = false)
    private int lineCount;

    @Column(updatable = false)
    private String reason;

    @Column(updatable = false)
    private String note;

    protected PlanRevision() {
    }

    public PlanRevision(UUID tenantId, UUID planId, int revision, UUID approvedBy, long totalMinor, int lineCount,
            String reason, String note, Instant now) {
        super(tenantId, now);
        this.planId = planId;
        this.revision = revision;
        this.approvedBy = approvedBy;
        this.approvedAt = now;
        this.totalMinor = totalMinor;
        this.lineCount = lineCount;
        this.reason = reason;
        this.note = note;
    }

    public UUID getPlanId() { return planId; }
    public int getRevision() { return revision; }
    public UUID getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public long getTotalMinor() { return totalMinor; }
    public int getLineCount() { return lineCount; }
    public String getReason() { return reason; }
    public String getNote() { return note; }
}
