package com.pmplatform.planning;

import com.pmplatform.platform.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Planned and earned % on a day progress changed: the points of the S-curve. */
@Entity
@Table(schema = "planning", name = "progress_snapshots")
public class ProgressSnapshot extends TenantEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "as_of", nullable = false, updatable = false)
    private LocalDate asOf;

    @Column(name = "planned_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal plannedPct;

    @Column(name = "earned_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal earnedPct;

    @Column(name = "recorded_by", nullable = false)
    private UUID recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected ProgressSnapshot() {
    }

    public ProgressSnapshot(UUID tenantId, UUID projectId, LocalDate asOf, Instant now) {
        super(tenantId, now);
        this.projectId = projectId;
        this.asOf = asOf;
    }

    public void record(BigDecimal planned, BigDecimal earned, UUID by, Instant now) {
        this.plannedPct = planned;
        this.earnedPct = earned;
        this.recordedBy = by;
        this.recordedAt = now;
        touch(now);
    }

    public UUID getProjectId() { return projectId; }
    public LocalDate getAsOf() { return asOf; }
    public BigDecimal getPlannedPct() { return plannedPct; }
    public BigDecimal getEarnedPct() { return earnedPct; }
    public UUID getRecordedBy() { return recordedBy; }
    public Instant getRecordedAt() { return recordedAt; }
}
