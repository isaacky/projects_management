package com.pmplatform.benefits;

import com.pmplatform.platform.id.UuidV7;
import com.pmplatform.platform.web.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** A benefit as assessed in a review: the figures are filled in, and frozen, when the review is completed. */
@Entity
@Table(schema = "benefits", name = "review_assessments")
public class ReviewAssessment {

    @Id
    private UUID id;

    @Column(name = "review_id", nullable = false, updatable = false)
    private UUID reviewId;

    @Column(name = "benefit_id", nullable = false, updatable = false)
    private UUID benefitId;

    @Column(name = "baseline_value")
    private BigDecimal baselineValue;

    @Column(name = "target_value")
    private BigDecimal targetValue;

    @Column(name = "actual_value")
    private BigDecimal actualValue;

    @Column(name = "measured_on")
    private LocalDate measuredOn;

    @Column(name = "progress_pct")
    private BigDecimal progressPct;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BenefitRating rating;

    private String comment;

    @Version
    private Long version;

    protected ReviewAssessment() {
    }

    public ReviewAssessment(UUID reviewId, UUID benefitId) {
        this.id = UuidV7.generate();
        this.reviewId = reviewId;
        this.benefitId = benefitId;
    }

    public void assess(BenefitRating rating, String comment) {
        if (rating == null) {
            throw new BusinessRuleException("Rate every benefit in the review");
        }
        this.rating = rating;
        this.comment = Text.optional(comment, 2000, "The comment");
    }

    public void freeze(BigDecimal baseline, BigDecimal target, BigDecimal actual, LocalDate measuredOn,
            BigDecimal progress) {
        this.baselineValue = baseline;
        this.targetValue = target;
        this.actualValue = actual;
        this.measuredOn = measuredOn;
        this.progressPct = progress;
    }

    public UUID getId() { return id; }
    public UUID getReviewId() { return reviewId; }
    public UUID getBenefitId() { return benefitId; }
    public BigDecimal getBaselineValue() { return baselineValue; }
    public BigDecimal getTargetValue() { return targetValue; }
    public BigDecimal getActualValue() { return actualValue; }
    public LocalDate getMeasuredOn() { return measuredOn; }
    public BigDecimal getProgressPct() { return progressPct; }
    public BenefitRating getRating() { return rating; }
    public String getComment() { return comment; }
}
