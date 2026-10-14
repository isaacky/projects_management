package com.pmplatform.contracts;

import com.pmplatform.platform.id.UuidV7;
import com.pmplatform.platform.web.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A requested change to the contract value and/or end date. Applied once fully approved. */
@Entity
@Table(schema = "contracts", name = "variations")
public class Variation {

    @Id
    private UUID id;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    @Column(nullable = false, updatable = false)
    private int number;

    @Column(name = "value_change_minor", nullable = false, updatable = false)
    private long valueChangeMinor;

    @Column(name = "new_end_date", updatable = false)
    private LocalDate newEndDate;

    @Column(nullable = false, updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VariationStatus status;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Version
    private Long version;

    protected Variation() {
    }

    public Variation(UUID contractId, int number, long valueChangeMinor, LocalDate newEndDate, String reason,
            UUID requestedBy, Instant now) {
        if (valueChangeMinor == 0 && newEndDate == null) {
            throw new BusinessRuleException("A variation changes the value, the end date, or both");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("Give the reason for the variation");
        }
        this.id = UuidV7.generate();
        this.contractId = contractId;
        this.number = number;
        this.valueChangeMinor = valueChangeMinor;
        this.newEndDate = newEndDate;
        this.reason = reason.trim();
        this.requestedBy = requestedBy;
        this.requestedAt = now;
        this.status = VariationStatus.PENDING;
    }

    public void decide(VariationStatus outcome, Instant now) {
        if (status != VariationStatus.PENDING) {
            throw new BusinessRuleException("Variation " + number + " was already " + status.name().toLowerCase());
        }
        status = outcome;
        decidedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getContractId() { return contractId; }
    public int getNumber() { return number; }
    public long getValueChangeMinor() { return valueChangeMinor; }
    public LocalDate getNewEndDate() { return newEndDate; }
    public String getReason() { return reason; }
    public VariationStatus getStatus() { return status; }
    public UUID getRequestedBy() { return requestedBy; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getDecidedAt() { return decidedAt; }
}
