package com.pmplatform.contracts;

import com.pmplatform.platform.id.UuidV7;
import com.pmplatform.platform.web.BusinessRuleException;
import com.pmplatform.platform.web.ForbiddenActionException;
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

/**
 * A supplier invoice and its payment certificate:
 * RECEIVED ──certify──▶ CERTIFIED ──approve──▶ APPROVED ──pay──▶ PAID, or REJECTED before payment.
 * The person who approves cannot be the one who certified.
 */
@Entity
@Table(schema = "contracts", name = "invoices")
public class Invoice {

    @Id
    private UUID id;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private InvoiceKind kind;

    @Column(name = "certificate_no", nullable = false, updatable = false)
    private int certificateNo;

    @Column(name = "supplier_ref", updatable = false)
    private String supplierRef;

    @Column(name = "invoice_date", nullable = false, updatable = false)
    private LocalDate invoiceDate;

    @Column(name = "milestone_id", updatable = false)
    private UUID milestoneId;

    @Column(name = "gross_minor", nullable = false, updatable = false)
    private long grossMinor;

    @Column(name = "retention_minor", nullable = false, updatable = false)
    private long retentionMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status;

    @Column(name = "received_by", nullable = false, updatable = false)
    private UUID receivedBy;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "certified_by")
    private UUID certifiedBy;

    @Column(name = "certified_at")
    private Instant certifiedAt;

    @Column(name = "certificate_note")
    private String certificateNote;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "paid_by")
    private UUID paidBy;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    @Column(name = "payment_ref")
    private String paymentRef;

    @Column(name = "rejected_reason")
    private String rejectedReason;

    @Version
    private Long version;

    protected Invoice() {
    }

    public Invoice(UUID contractId, InvoiceKind kind, int certificateNo, String supplierRef, LocalDate invoiceDate,
            UUID milestoneId, long grossMinor, long retentionMinor, UUID receivedBy, Instant now) {
        if (grossMinor <= 0) {
            throw new BusinessRuleException("The invoice amount must be above zero");
        }
        if (retentionMinor < 0 || retentionMinor > grossMinor) {
            throw new IllegalArgumentException("retention out of range");
        }
        this.id = UuidV7.generate();
        this.contractId = contractId;
        this.kind = kind;
        this.certificateNo = certificateNo;
        this.supplierRef = supplierRef == null || supplierRef.isBlank() ? null : supplierRef.trim();
        this.invoiceDate = invoiceDate;
        this.milestoneId = milestoneId;
        this.grossMinor = grossMinor;
        this.retentionMinor = retentionMinor;
        this.receivedBy = receivedBy;
        this.receivedAt = now;
        this.status = InvoiceStatus.RECEIVED;
    }

    public void certify(UUID by, String note, Instant now) {
        require(InvoiceStatus.RECEIVED, "certified");
        if (note == null || note.isBlank()) {
            throw new BusinessRuleException("Record what was checked (work done, quantities, deliveries)");
        }
        status = InvoiceStatus.CERTIFIED;
        certifiedBy = by;
        certifiedAt = now;
        certificateNote = note.trim();
    }

    public void approve(UUID by, Instant now) {
        require(InvoiceStatus.CERTIFIED, "approved");
        if (by.equals(certifiedBy)) {
            throw new ForbiddenActionException("The person who certified a payment cannot also approve it");
        }
        status = InvoiceStatus.APPROVED;
        approvedBy = by;
        approvedAt = now;
    }

    public void pay(UUID by, LocalDate on, String reference) {
        require(InvoiceStatus.APPROVED, "paid");
        if (reference == null || reference.isBlank()) {
            throw new BusinessRuleException("Record the payment reference (for example the EFT number)");
        }
        status = InvoiceStatus.PAID;
        paidBy = by;
        paidOn = on;
        paymentRef = reference.trim();
    }

    public void reject(String reason) {
        if (status == InvoiceStatus.PAID || status == InvoiceStatus.REJECTED) {
            throw new BusinessRuleException("A " + status.name().toLowerCase() + " invoice cannot be rejected");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("Give the reason for rejecting the invoice");
        }
        status = InvoiceStatus.REJECTED;
        rejectedReason = reason.trim();
    }

    /** What the supplier is paid: the gross amount less retention. */
    public long netMinor() {
        return grossMinor - retentionMinor;
    }

    public boolean counts() {
        return status != InvoiceStatus.REJECTED;
    }

    private void require(InvoiceStatus expected, String what) {
        if (status != expected) {
            throw new BusinessRuleException("Certificate " + certificateNo + " is " + status.name().toLowerCase()
                    + " and cannot be " + what);
        }
    }

    public UUID getId() { return id; }
    public UUID getContractId() { return contractId; }
    public InvoiceKind getKind() { return kind; }
    public int getCertificateNo() { return certificateNo; }
    public String getSupplierRef() { return supplierRef; }
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public UUID getMilestoneId() { return milestoneId; }
    public long getGrossMinor() { return grossMinor; }
    public long getRetentionMinor() { return retentionMinor; }
    public InvoiceStatus getStatus() { return status; }
    public UUID getReceivedBy() { return receivedBy; }
    public Instant getReceivedAt() { return receivedAt; }
    public UUID getCertifiedBy() { return certifiedBy; }
    public Instant getCertifiedAt() { return certifiedAt; }
    public String getCertificateNote() { return certificateNote; }
    public UUID getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public UUID getPaidBy() { return paidBy; }
    public LocalDate getPaidOn() { return paidOn; }
    public String getPaymentRef() { return paymentRef; }
    public String getRejectedReason() { return rejectedReason; }
}
