package com.pmplatform.benefits;

import com.pmplatform.platform.id.UuidV7;
import com.pmplatform.platform.web.BusinessRuleException;
import com.pmplatform.portfolio.ObjectiveOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

/** Whether one project objective was met, as found by a review; the wording is frozen on completion. */
@Entity
@Table(schema = "benefits", name = "review_objective_assessments")
public class ReviewObjectiveAssessment {

    @Id
    private UUID id;

    @Column(name = "review_id", nullable = false, updatable = false)
    private UUID reviewId;

    @Column(name = "objective_id", nullable = false, updatable = false)
    private UUID objectiveId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ObjectiveOutcome outcome;

    private String evidence;

    private String statement;

    @Version
    private Long version;

    protected ReviewObjectiveAssessment() {
    }

    public ReviewObjectiveAssessment(UUID reviewId, UUID objectiveId) {
        this.id = UuidV7.generate();
        this.reviewId = reviewId;
        this.objectiveId = objectiveId;
    }

    public void assess(ObjectiveOutcome outcome, String evidence) {
        if (outcome == null) {
            throw new BusinessRuleException("Say whether each objective was met");
        }
        this.outcome = outcome;
        this.evidence = Text.optional(evidence, 2000, "The evidence");
    }

    public void freeze(String statement) {
        this.statement = statement;
    }

    public UUID getId() { return id; }
    public UUID getReviewId() { return reviewId; }
    public UUID getObjectiveId() { return objectiveId; }
    public ObjectiveOutcome getOutcome() { return outcome; }
    public String getEvidence() { return evidence; }
    public String getStatement() { return statement; }
}
