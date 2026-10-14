package com.pmplatform.benefits;

import com.pmplatform.platform.persistence.TenantEntity;
import com.pmplatform.platform.web.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One reading of a benefit's measure. */
@Entity
@Table(schema = "benefits", name = "measurements")
public class Measurement extends TenantEntity {

    @Column(name = "benefit_id", nullable = false, updatable = false)
    private UUID benefitId;

    @Column(name = "measured_on", nullable = false)
    private LocalDate measuredOn;

    @Column(nullable = false)
    private BigDecimal value;

    private String source;

    private String note;

    @Column(name = "recorded_by", nullable = false)
    private UUID recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected Measurement() {
    }

    public Measurement(UUID tenantId, UUID benefitId, Instant now) {
        super(tenantId, now);
        this.benefitId = benefitId;
    }

    public void record(Benefit b, LocalDate on, BigDecimal value, String source, String note, LocalDate today,
            UUID by, Instant now) {
        if (on == null || value == null) {
            throw new BusinessRuleException("Give the date and the value");
        }
        if (on.isBefore(b.getBaselineDate())) {
            throw new BusinessRuleException("A measurement cannot be earlier than the baseline date ("
                    + b.getBaselineDate() + ")");
        }
        if (on.isAfter(today)) {
            throw new BusinessRuleException("A measurement cannot be in the future");
        }
        Benefit.checkScale(value, "value");
        this.measuredOn = on;
        this.value = value;
        this.source = Text.optional(source, 200, "The source");
        this.note = Text.optional(note, 1000, "The note");
        this.recordedBy = by;
        this.recordedAt = now;
        touch(now);
    }

    public UUID getBenefitId() { return benefitId; }
    public LocalDate getMeasuredOn() { return measuredOn; }
    public BigDecimal getValue() { return value; }
    public String getSource() { return source; }
    public String getNote() { return note; }
    public UUID getRecordedBy() { return recordedBy; }
    public Instant getRecordedAt() { return recordedAt; }
}
