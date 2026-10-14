package com.pmplatform.benefits;

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

/** A benefit the project should deliver, measured by a KPI from a baseline to a target. */
@Entity
@Table(schema = "benefits", name = "benefits")
public class Benefit extends TenantEntity {

    /** What is entered for a benefit. */
    public record Definition(String title, String description, BenefitCategory category, String objective,
            UUID ownerId, String measure, String unit, BigDecimal baselineValue, LocalDate baselineDate,
            BigDecimal targetValue, LocalDate targetDate, Frequency frequency, Long annualValueMinor) {
    }

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(nullable = false, updatable = false)
    private int number;

    @Column(nullable = false)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BenefitCategory category;

    private String objective;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String measure;

    @Column(nullable = false)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Direction direction;

    @Column(name = "baseline_value", nullable = false)
    private BigDecimal baselineValue;

    @Column(name = "baseline_date", nullable = false)
    private LocalDate baselineDate;

    @Column(name = "target_value", nullable = false)
    private BigDecimal targetValue;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Frequency frequency;

    @Column(name = "annual_value_minor")
    private Long annualValueMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BenefitStatus status;

    @Column(name = "closed_note")
    private String closedNote;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "reminded_due")
    private LocalDate remindedDue;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Benefit() {
    }

    public Benefit(UUID tenantId, UUID projectId, int number, UUID createdBy, Instant now) {
        super(tenantId, now);
        this.projectId = projectId;
        this.number = number;
        this.status = BenefitStatus.ACTIVE;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public void define(Definition d, Instant now) {
        requireActive("changed");
        if (d.category() == null || d.ownerId() == null || d.frequency() == null) {
            throw new BusinessRuleException("Give the category, the owner and how often it is measured");
        }
        if (d.baselineValue() == null || d.targetValue() == null || d.baselineDate() == null
                || d.targetDate() == null) {
            throw new BusinessRuleException("Give the baseline and the target, each with its date");
        }
        if (d.targetValue().compareTo(d.baselineValue()) == 0) {
            throw new BusinessRuleException("The target must differ from the baseline");
        }
        if (!d.targetDate().isAfter(d.baselineDate())) {
            throw new BusinessRuleException("The target date must be after the baseline date");
        }
        checkScale(d.baselineValue(), "baseline");
        checkScale(d.targetValue(), "target");
        if (d.annualValueMinor() != null && d.annualValueMinor() < 0) {
            throw new BusinessRuleException("The yearly value cannot be negative");
        }
        this.title = Text.required(d.title(), 200, "The title");
        this.description = Text.optional(d.description(), 2000, "The description");
        this.category = d.category();
        this.objective = Text.optional(d.objective(), 500, "The objective");
        if (!d.ownerId().equals(this.ownerId)) {
            this.remindedDue = null; // a new owner hears about the current due date
        }
        this.ownerId = d.ownerId();
        this.measure = Text.required(d.measure(), 200, "The measure");
        this.unit = Text.required(d.unit(), 32, "The unit");
        this.baselineValue = d.baselineValue();
        this.baselineDate = d.baselineDate();
        this.targetValue = d.targetValue();
        this.targetDate = d.targetDate();
        this.direction = BenefitRules.direction(d.baselineValue(), d.targetValue());
        this.frequency = d.frequency();
        this.annualValueMinor = d.annualValueMinor();
        touch(now);
    }

    static void checkScale(BigDecimal v, String what) {
        BigDecimal s = v.stripTrailingZeros();
        if (Math.max(s.scale(), 0) > 4 || s.precision() - s.scale() > 14) {
            throw new BusinessRuleException("The " + what + " has too many digits (up to 14 before and 4 after "
                    + "the decimal point)");
        }
    }

    public void close(BenefitStatus outcome, String note, UUID by, Instant now) {
        requireActive("closed");
        if (outcome == null || outcome == BenefitStatus.ACTIVE) {
            throw new BusinessRuleException("Close the benefit as achieved, not achieved or cancelled");
        }
        this.closedNote = Text.required(note, 2000, "The reason");
        this.status = outcome;
        this.closedBy = by;
        this.closedAt = now;
        touch(now);
    }

    public void reopen(Instant now) {
        if (status.isOpen()) {
            throw new BusinessRuleException("The benefit is already open");
        }
        this.status = BenefitStatus.ACTIVE;
        this.closedNote = null;
        this.closedBy = null;
        this.closedAt = null;
        touch(now);
    }

    public void requireActive(String what) {
        if (!status.isOpen()) {
            throw new BusinessRuleException("B" + number + " is " + status.name().toLowerCase().replace('_', ' ')
                    + " and cannot be " + what);
        }
    }

    public void reminded(LocalDate due) {
        this.remindedDue = due;
    }

    public UUID getProjectId() { return projectId; }
    public int getNumber() { return number; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public BenefitCategory getCategory() { return category; }
    public String getObjective() { return objective; }
    public UUID getOwnerId() { return ownerId; }
    public String getMeasure() { return measure; }
    public String getUnit() { return unit; }
    public Direction getDirection() { return direction; }
    public BigDecimal getBaselineValue() { return baselineValue; }
    public LocalDate getBaselineDate() { return baselineDate; }
    public BigDecimal getTargetValue() { return targetValue; }
    public LocalDate getTargetDate() { return targetDate; }
    public Frequency getFrequency() { return frequency; }
    public Long getAnnualValueMinor() { return annualValueMinor; }
    public BenefitStatus getStatus() { return status; }
    public String getClosedNote() { return closedNote; }
    public UUID getClosedBy() { return closedBy; }
    public Instant getClosedAt() { return closedAt; }
    public LocalDate getRemindedDue() { return remindedDue; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
