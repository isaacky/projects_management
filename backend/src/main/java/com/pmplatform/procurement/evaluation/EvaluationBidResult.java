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

/** The committee's result for one bid, fixed as each stage is closed. */
@Entity
@Table(schema = "procurement", name = "evaluation_bid_results")
public class EvaluationBidResult {

    @Id
    private UUID id;

    @Column(name = "evaluation_id", nullable = false, updatable = false)
    private UUID evaluationId;

    @Column(name = "bid_id", nullable = false, updatable = false)
    private UUID bidId;

    @Column(name = "preliminary_passed")
    private Boolean preliminaryPassed;

    @Column(name = "preliminary_note")
    private String preliminaryNote;

    @Column(name = "technical_score", precision = 8, scale = 2)
    private BigDecimal technicalScore;

    @Column(name = "technical_pct", precision = 5, scale = 2)
    private BigDecimal technicalPct;

    @Column(name = "technical_passed")
    private Boolean technicalPassed;

    @Column(name = "evaluated_price_minor")
    private Long evaluatedPriceMinor;

    @Column(name = "price_note")
    private String priceNote;

    @Column(name = "financial_rank")
    private Integer financialRank;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected EvaluationBidResult() {
    }

    public EvaluationBidResult(UUID evaluationId, UUID bidId, Instant now) {
        this.id = UuidV7.generate();
        this.evaluationId = evaluationId;
        this.bidId = bidId;
        this.updatedAt = now;
    }

    public void preliminary(boolean passed, String note, Instant now) {
        this.preliminaryPassed = passed;
        this.preliminaryNote = note;
        this.updatedAt = now;
    }

    public void technical(BigDecimal score, BigDecimal pct, boolean passed, Instant now) {
        this.technicalScore = score;
        this.technicalPct = pct;
        this.technicalPassed = passed;
        this.updatedAt = now;
    }

    public void price(Long evaluatedPriceMinor, String note, Instant now) {
        this.evaluatedPriceMinor = evaluatedPriceMinor;
        this.priceNote = note == null || note.isBlank() ? null : note.trim();
        this.updatedAt = now;
    }

    public void rank(Integer rank, Instant now) {
        this.financialRank = rank;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getEvaluationId() { return evaluationId; }
    public UUID getBidId() { return bidId; }
    public Boolean getPreliminaryPassed() { return preliminaryPassed; }
    public String getPreliminaryNote() { return preliminaryNote; }
    public BigDecimal getTechnicalScore() { return technicalScore; }
    public BigDecimal getTechnicalPct() { return technicalPct; }
    public Boolean getTechnicalPassed() { return technicalPassed; }
    public Long getEvaluatedPriceMinor() { return evaluatedPriceMinor; }
    public String getPriceNote() { return priceNote; }
    public Integer getFinancialRank() { return financialRank; }
    public Instant getUpdatedAt() { return updatedAt; }
}
