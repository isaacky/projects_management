package com.pmplatform.procurement.evaluation;

import com.pmplatform.platform.id.UuidV7;
import com.pmplatform.procurement.config.CriterionStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.UUID;

/** A criterion of one evaluation, copied from a template and adjustable until the committee is appointed. */
@Entity
@Table(schema = "procurement", name = "evaluation_criteria")
public class EvaluationCriterion {

    @Id
    private UUID id;

    @Column(name = "evaluation_id", nullable = false, updatable = false)
    private UUID evaluationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private CriterionStage stage;

    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;

    @Column(nullable = false, updatable = false)
    private String name;

    @Column(updatable = false)
    private String description;

    @Column(name = "max_score", precision = 6, scale = 2, updatable = false)
    private BigDecimal maxScore;

    @Version
    private Long version;

    protected EvaluationCriterion() {
    }

    public EvaluationCriterion(UUID evaluationId, CriterionStage stage, int sortOrder, String name,
            String description, BigDecimal maxScore) {
        this.id = UuidV7.generate();
        this.evaluationId = evaluationId;
        this.stage = stage;
        this.sortOrder = sortOrder;
        this.name = name;
        this.description = description;
        this.maxScore = maxScore;
    }

    public UUID getId() { return id; }
    public UUID getEvaluationId() { return evaluationId; }
    public CriterionStage getStage() { return stage; }
    public int getSortOrder() { return sortOrder; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getMaxScore() { return maxScore; }
}
