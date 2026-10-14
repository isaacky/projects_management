package com.pmplatform.procurement.evaluation;

import com.pmplatform.platform.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One member's mark for one bid on one criterion: pass/fail (preliminary) or a score (technical). */
@Entity
@Table(schema = "procurement", name = "evaluation_scores")
public class EvaluationScore {

    @Id
    private UUID id;

    @Column(name = "evaluation_id", nullable = false, updatable = false)
    private UUID evaluationId;

    @Column(name = "criterion_id", nullable = false, updatable = false)
    private UUID criterionId;

    @Column(name = "bid_id", nullable = false, updatable = false)
    private UUID bidId;

    @Column(name = "member_id", nullable = false, updatable = false)
    private UUID memberId;

    private Boolean passed;

    @Column(precision = 6, scale = 2)
    private BigDecimal score;

    private String comment;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected EvaluationScore() {
    }

    public EvaluationScore(UUID evaluationId, UUID criterionId, UUID bidId, UUID memberId) {
        this.id = UuidV7.generate();
        this.evaluationId = evaluationId;
        this.criterionId = criterionId;
        this.bidId = bidId;
        this.memberId = memberId;
    }

    public void set(Boolean passed, BigDecimal score, String comment, Instant now) {
        this.passed = passed;
        this.score = score;
        this.comment = comment == null || comment.isBlank() ? null : comment.trim();
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getEvaluationId() { return evaluationId; }
    public UUID getCriterionId() { return criterionId; }
    public UUID getBidId() { return bidId; }
    public UUID getMemberId() { return memberId; }
    public Boolean getPassed() { return passed; }
    public BigDecimal getScore() { return score; }
    public String getComment() { return comment; }
    public Instant getUpdatedAt() { return updatedAt; }
}
