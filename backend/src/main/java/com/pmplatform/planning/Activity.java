package com.pmplatform.planning;

import com.pmplatform.platform.persistence.TenantEntity;
import com.pmplatform.platform.web.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One row of the schedule: a summary (with children) or an activity with a duration. */
@Entity
@Table(schema = "planning", name = "activities")
public class Activity extends TenantEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(name = "earliest_start")
    private LocalDate earliestStart;

    @Column(precision = 10, scale = 2)
    private BigDecimal weight;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "baseline_start")
    private LocalDate baselineStart;

    @Column(name = "baseline_finish")
    private LocalDate baselineFinish;

    @Column(name = "actual_start")
    private LocalDate actualStart;

    @Column(name = "actual_finish")
    private LocalDate actualFinish;

    @Column(name = "percent_complete", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentComplete = BigDecimal.ZERO;

    @Column(name = "progress_note")
    private String progressNote;

    @Column(name = "progress_updated_at")
    private Instant progressUpdatedAt;

    @Column(name = "progress_updated_by")
    private UUID progressUpdatedBy;

    protected Activity() {
    }

    public Activity(UUID tenantId, UUID projectId, Instant now) {
        super(tenantId, now);
        this.projectId = projectId;
    }

    public void define(UUID parentId, int sortOrder, String name, String description, int durationDays,
            LocalDate earliestStart, BigDecimal weight, UUID ownerId, Instant now) {
        if (durationDays < 0 || durationDays > 3650) {
            throw new BusinessRuleException("\"" + name + "\": the duration is from 0 (a milestone) to 3650 days");
        }
        if (weight != null && weight.signum() < 0) {
            throw new BusinessRuleException("\"" + name + "\": the weight cannot be negative");
        }
        this.parentId = parentId;
        this.sortOrder = sortOrder;
        this.name = name.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.durationDays = durationDays;
        this.earliestStart = earliestStart;
        this.weight = weight;
        this.ownerId = ownerId;
        touch(now);
    }

    public void baseline(LocalDate start, LocalDate finish, Instant now) {
        this.baselineStart = start;
        this.baselineFinish = finish;
        touch(now);
    }

    /**
     * Records progress. A finish date means 100%; any progress needs a start date; dates cannot be
     * in the future.
     */
    public void progress(LocalDate start, LocalDate finish, BigDecimal percent, String note, LocalDate today,
            UUID by, Instant now) {
        BigDecimal pct = finish != null ? BigDecimal.valueOf(100) : percent == null ? BigDecimal.ZERO : percent;
        if (pct.signum() < 0 || pct.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessRuleException("Percent complete is from 0 to 100");
        }
        if (finish == null && pct.compareTo(BigDecimal.valueOf(100)) == 0) {
            throw new BusinessRuleException("Enter the actual finish date for a completed activity");
        }
        if (pct.signum() > 0 && start == null) {
            throw new BusinessRuleException("Enter the actual start date");
        }
        if ((start != null && start.isAfter(today)) || (finish != null && finish.isAfter(today))) {
            throw new BusinessRuleException("Actual dates cannot be in the future");
        }
        if (start != null && finish != null && finish.isBefore(start)) {
            throw new BusinessRuleException("The actual finish cannot be before the actual start");
        }
        this.actualStart = start;
        this.actualFinish = finish;
        this.percentComplete = pct.setScale(2, java.math.RoundingMode.HALF_UP);
        this.progressNote = note == null || note.isBlank() ? null : note.trim();
        this.progressUpdatedAt = now;
        this.progressUpdatedBy = by;
        touch(now);
    }

    public boolean hasProgress() {
        return actualStart != null || percentComplete.signum() > 0;
    }

    public ScheduleEngine.Task toTask() {
        return new ScheduleEngine.Task(getId(), parentId, sortOrder, name, durationDays, earliestStart, weight,
                baselineStart, baselineFinish, actualStart, actualFinish, percentComplete);
    }

    public UUID getProjectId() { return projectId; }
    public UUID getParentId() { return parentId; }
    public int getSortOrder() { return sortOrder; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getDurationDays() { return durationDays; }
    public LocalDate getEarliestStart() { return earliestStart; }
    public BigDecimal getWeight() { return weight; }
    public UUID getOwnerId() { return ownerId; }
    public LocalDate getBaselineStart() { return baselineStart; }
    public LocalDate getBaselineFinish() { return baselineFinish; }
    public LocalDate getActualStart() { return actualStart; }
    public LocalDate getActualFinish() { return actualFinish; }
    public BigDecimal getPercentComplete() { return percentComplete; }
    public String getProgressNote() { return progressNote; }
    public Instant getProgressUpdatedAt() { return progressUpdatedAt; }
    public UUID getProgressUpdatedBy() { return progressUpdatedBy; }
}
