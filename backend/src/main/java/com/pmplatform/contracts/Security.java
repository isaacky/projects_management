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

/** A performance or advance payment security held for a contract. */
@Entity
@Table(schema = "contracts", name = "securities")
public class Security {

    @Id
    private UUID id;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private SecurityKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private SecurityForm form;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @Column(nullable = false, updatable = false)
    private String issuer;

    @Column(nullable = false, updatable = false)
    private String reference;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SecurityStatus status;

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDate receivedAt;

    @Column(name = "recorded_by", nullable = false, updatable = false)
    private UUID recordedBy;

    @Column(name = "expiry_notified_at")
    private Instant expiryNotifiedAt;

    @Column(name = "closed_note")
    private String closedNote;

    @Version
    private Long version;

    protected Security() {
    }

    public Security(UUID contractId, SecurityKind kind, SecurityForm form, long amountMinor, String issuer,
            String reference, LocalDate expiryDate, LocalDate receivedAt, UUID recordedBy) {
        if (amountMinor <= 0) {
            throw new BusinessRuleException("The security amount must be above zero");
        }
        if (expiryDate.isBefore(receivedAt)) {
            throw new BusinessRuleException("The security expires before it was received");
        }
        this.id = UuidV7.generate();
        this.contractId = contractId;
        this.kind = kind;
        this.form = form;
        this.amountMinor = amountMinor;
        this.issuer = issuer.trim();
        this.reference = reference.trim();
        this.expiryDate = expiryDate;
        this.receivedAt = receivedAt;
        this.recordedBy = recordedBy;
        this.status = SecurityStatus.HELD;
    }

    /** A renewal or extension from the issuer moves the expiry date later. */
    public void extend(LocalDate newExpiry) {
        requireHeld();
        if (!newExpiry.isAfter(expiryDate)) {
            throw new BusinessRuleException("The new expiry date must be later than " + expiryDate);
        }
        expiryDate = newExpiry;
        expiryNotifiedAt = null;
    }

    public void close(SecurityStatus outcome, String note) {
        requireHeld();
        if (outcome == SecurityStatus.HELD) {
            throw new BusinessRuleException("Choose released or called");
        }
        if (note == null || note.isBlank()) {
            throw new BusinessRuleException("Record why the security is " + outcome.name().toLowerCase());
        }
        status = outcome;
        closedNote = note.trim();
    }

    public void markExpiryNotified(Instant now) {
        expiryNotifiedAt = now;
    }

    private void requireHeld() {
        if (status != SecurityStatus.HELD) {
            throw new BusinessRuleException("This security was already " + status.name().toLowerCase());
        }
    }

    public UUID getId() { return id; }
    public UUID getContractId() { return contractId; }
    public SecurityKind getKind() { return kind; }
    public SecurityForm getForm() { return form; }
    public long getAmountMinor() { return amountMinor; }
    public String getIssuer() { return issuer; }
    public String getReference() { return reference; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public SecurityStatus getStatus() { return status; }
    public LocalDate getReceivedAt() { return receivedAt; }
    public UUID getRecordedBy() { return recordedBy; }
    public Instant getExpiryNotifiedAt() { return expiryNotifiedAt; }
    public String getClosedNote() { return closedNote; }
}
