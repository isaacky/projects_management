package com.pmplatform.procurement.tender;

import com.pmplatform.platform.persistence.TenantEntity;
import com.pmplatform.platform.web.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A supplier's bid on a tender, as received by procurement staff. */
@Entity
@Table(schema = "procurement", name = "bids")
public class Bid extends TenantEntity {

    @Column(name = "tender_id", nullable = false, updatable = false)
    private UUID tenderId;

    @Column(name = "supplier_id", nullable = false, updatable = false)
    private UUID supplierId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BidStatus status;

    @Column(name = "receipt_no", nullable = false, updatable = false)
    private String receiptNo;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "received_by", nullable = false, updatable = false)
    private UUID receivedBy;

    @Column(name = "delivery_note", updatable = false)
    private String deliveryNote;

    @Column(name = "receipt_hash", nullable = false, updatable = false)
    private String receiptHash;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "withdraw_reason")
    private String withdrawReason;

    @Column(name = "read_out_price_minor")
    private Long readOutPriceMinor;

    @Column(name = "read_out_note")
    private String readOutNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "disqualified_stage")
    private EvaluationStage disqualifiedStage;

    @Column(name = "disqualified_reason")
    private String disqualifiedReason;

    protected Bid() {
    }

    public Bid(UUID tenantId, UUID tenderId, UUID supplierId, String receiptNo, Instant receivedAt,
            UUID receivedBy, String deliveryNote, String receiptHash) {
        super(tenantId, receivedAt);
        this.tenderId = tenderId;
        this.supplierId = supplierId;
        this.status = BidStatus.RECEIVED;
        this.receiptNo = receiptNo;
        this.receivedAt = receivedAt;
        this.receivedBy = receivedBy;
        this.deliveryNote = deliveryNote;
        this.receiptHash = receiptHash;
    }

    public void withdraw(String reason, Instant now) {
        if (status != BidStatus.RECEIVED) {
            throw new BusinessRuleException("Bid " + receiptNo + " is already withdrawn");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("Record the supplier's reason for withdrawing");
        }
        status = BidStatus.WITHDRAWN;
        withdrawnAt = now;
        withdrawReason = reason.trim();
        touch(now);
    }

    public void recordReadOut(Long priceMinor, String note, Instant now) {
        if (status != BidStatus.RECEIVED) {
            throw new BusinessRuleException("A withdrawn bid has no read-out price");
        }
        if (priceMinor != null && priceMinor < 0) {
            throw new BusinessRuleException("The price cannot be negative");
        }
        readOutPriceMinor = priceMinor;
        readOutNote = note == null || note.isBlank() ? null : note.trim();
        touch(now);
    }

    /** Recorded by the evaluation committee when the bid fails a stage. */
    public void disqualify(EvaluationStage stage, String reason, Instant now) {
        if (status != BidStatus.RECEIVED) {
            throw new BusinessRuleException("Bid " + receiptNo + " was withdrawn");
        }
        if (disqualifiedStage != null) {
            throw new BusinessRuleException("Bid " + receiptNo + " was already found non-responsive at the "
                    + disqualifiedStage.name().toLowerCase(java.util.Locale.ROOT) + " stage");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("Give the reason the bid is non-responsive");
        }
        disqualifiedStage = stage;
        disqualifiedReason = reason.trim();
        touch(now);
    }

    /** Received, not withdrawn, and not knocked out by the evaluation so far. */
    public boolean isInContention() {
        return status == BidStatus.RECEIVED && disqualifiedStage == null;
    }

    public EvaluationStage getDisqualifiedStage() { return disqualifiedStage; }
    public String getDisqualifiedReason() { return disqualifiedReason; }
    public UUID getTenderId() { return tenderId; }
    public UUID getSupplierId() { return supplierId; }
    public BidStatus getStatus() { return status; }
    public String getReceiptNo() { return receiptNo; }
    public Instant getReceivedAt() { return receivedAt; }
    public UUID getReceivedBy() { return receivedBy; }
    public String getDeliveryNote() { return deliveryNote; }
    public String getReceiptHash() { return receiptHash; }
    public Instant getWithdrawnAt() { return withdrawnAt; }
    public String getWithdrawReason() { return withdrawReason; }
    public Long getReadOutPriceMinor() { return readOutPriceMinor; }
    public String getReadOutNote() { return readOutNote; }
}
