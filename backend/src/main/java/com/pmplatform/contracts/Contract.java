package com.pmplatform.contracts;

import com.pmplatform.platform.persistence.TenantEntity;
import com.pmplatform.platform.web.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A contract made from an awarded tender.
 *
 * <pre>
 *   DRAFT ──sign (after the standstill period)──▶ ACTIVE ──complete──▶ COMPLETED
 *     │                                              └──terminate──▶ TERMINATED
 *     └──cancel (e.g. review upheld)──▶ CANCELLED
 * </pre>
 */
@Entity
@Table(schema = "contracts", name = "contracts")
public class Contract extends TenantEntity {

    @Column(nullable = false, updatable = false)
    private String number;

    @Column(name = "tender_id", nullable = false, updatable = false)
    private UUID tenderId;

    @Column(name = "bid_id", nullable = false, updatable = false)
    private UUID bidId;

    @Column(name = "supplier_id", nullable = false, updatable = false)
    private UUID supplierId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "requisition_id", nullable = false, updatable = false)
    private UUID requisitionId;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractStatus status;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "original_value_minor", nullable = false, updatable = false)
    private long originalValueMinor;

    @Column(name = "current_value_minor", nullable = false)
    private long currentValueMinor;

    @Column(name = "retention_bp", nullable = false)
    private int retentionBp;

    @Column(name = "defects_liability_days", nullable = false)
    private int defectsLiabilityDays;

    @Column(name = "performance_security_minor", nullable = false)
    private long performanceSecurityMinor;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "original_end_date")
    private LocalDate originalEndDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "contract_manager_id")
    private UUID contractManagerId;

    @Column(name = "standstill_ends_at", nullable = false, updatable = false)
    private Instant standstillEndsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false)
    private ReviewStatus reviewStatus = ReviewStatus.NONE;

    @Column(name = "review_note")
    private String reviewNote;

    @Column(name = "signed_at")
    private LocalDate signedAt;

    @Column(name = "supplier_signatory")
    private String supplierSignatory;

    @Column(name = "entity_signatory")
    private String entitySignatory;

    @Column(name = "signed_recorded_by")
    private UUID signedRecordedBy;

    @Column(name = "completed_at")
    private LocalDate completedAt;

    @Column(name = "ended_reason")
    private String endedReason;

    @Column(name = "rating_quality")
    private Integer ratingQuality;

    @Column(name = "rating_timeliness")
    private Integer ratingTimeliness;

    @Column(name = "rating_compliance")
    private Integer ratingCompliance;

    @Column(name = "rating_comment")
    private String ratingComment;

    @Column(name = "rated_by")
    private UUID ratedBy;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Contract() {
    }

    public Contract(UUID tenantId, String number, UUID tenderId, UUID bidId, UUID supplierId, UUID projectId,
            UUID requisitionId, String title, String currency, long valueMinor, Instant standstillEndsAt,
            UUID createdBy, Instant now) {
        super(tenantId, now);
        this.number = number;
        this.tenderId = tenderId;
        this.bidId = bidId;
        this.supplierId = supplierId;
        this.projectId = projectId;
        this.requisitionId = requisitionId;
        this.title = title;
        this.currency = currency;
        this.originalValueMinor = valueMinor;
        this.currentValueMinor = valueMinor;
        this.standstillEndsAt = standstillEndsAt;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.status = ContractStatus.DRAFT;
    }

    // ------------------------------------------------------------------ draft

    public void editDraft(String title, LocalDate start, LocalDate end, int retentionBp, int defectsLiabilityDays,
            long performanceSecurityMinor, UUID contractManagerId, Instant now) {
        requireStatus(ContractStatus.DRAFT, "be edited");
        if (start != null && end != null && end.isBefore(start)) {
            throw new BusinessRuleException("The end date cannot be before the start date");
        }
        if (retentionBp < 0 || retentionBp > 2000) {
            throw new BusinessRuleException("Retention is between 0% and 20%");
        }
        this.title = title;
        this.startDate = start;
        this.endDate = end;
        this.originalEndDate = end;
        this.retentionBp = retentionBp;
        this.defectsLiabilityDays = defectsLiabilityDays;
        this.performanceSecurityMinor = performanceSecurityMinor;
        this.contractManagerId = contractManagerId;
        touch(now);
    }

    public void recordReview(ReviewStatus review, String note, Instant now) {
        requireStatus(ContractStatus.DRAFT, "have its review status changed");
        if (review == ReviewStatus.NONE) {
            throw new BusinessRuleException("Choose lodged, dismissed or upheld");
        }
        if (note == null || note.isBlank()) {
            throw new BusinessRuleException("Record the review reference and outcome");
        }
        this.reviewStatus = review;
        this.reviewNote = note.trim();
        if (review == ReviewStatus.UPHELD) {
            this.status = ContractStatus.CANCELLED;
            this.endedReason = "Review Board upheld a request for review: " + note.trim();
        }
        touch(now);
    }

    public void cancel(String reason, Instant now) {
        requireStatus(ContractStatus.DRAFT, "be cancelled");
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("Give the reason for cancelling");
        }
        status = ContractStatus.CANCELLED;
        endedReason = reason.trim();
        touch(now);
    }

    /** Signing; the service checks documents, securities and milestones first (see {@link ContractRules}). */
    public void sign(LocalDate signedOn, String supplierSignatory, String entitySignatory, UUID recordedBy, Instant now) {
        requireStatus(ContractStatus.DRAFT, "be signed");
        if (now.isBefore(standstillEndsAt)) {
            throw new BusinessRuleException("The contract cannot be signed before the standstill period ends ("
                    + standstillEndsAt + ")");
        }
        if (reviewStatus == ReviewStatus.LODGED) {
            throw new BusinessRuleException("A request for review is pending; wait for the Review Board's decision");
        }
        if (supplierSignatory == null || supplierSignatory.isBlank() || entitySignatory == null
                || entitySignatory.isBlank()) {
            throw new BusinessRuleException("Record who signed for the supplier and for the organisation");
        }
        this.signedAt = signedOn;
        this.supplierSignatory = supplierSignatory.trim();
        this.entitySignatory = entitySignatory.trim();
        this.signedRecordedBy = recordedBy;
        this.status = ContractStatus.ACTIVE;
        touch(now);
    }

    // ------------------------------------------------------------------ delivery

    public void applyVariation(long valueChangeMinor, LocalDate newEndDate, Instant now) {
        // Callers check the 25% cap and that the value stays above what is already invoiced.
        requireStatus(ContractStatus.ACTIVE, "be varied");
        long next = currentValueMinor + valueChangeMinor;
        if (next < 0) {
            throw new BusinessRuleException("The variation would make the contract value negative");
        }
        currentValueMinor = next;
        if (newEndDate != null) {
            endDate = newEndDate;
        }
        touch(now);
    }

    public void complete(LocalDate on, int quality, int timeliness, int compliance, String comment, UUID by,
            Instant now) {
        requireStatus(ContractStatus.ACTIVE, "be completed");
        for (int r : new int[] {quality, timeliness, compliance}) {
            if (r < 1 || r > 5) {
                throw new BusinessRuleException("Ratings are from 1 (poor) to 5 (excellent)");
            }
        }
        this.completedAt = on;
        this.ratingQuality = quality;
        this.ratingTimeliness = timeliness;
        this.ratingCompliance = compliance;
        this.ratingComment = comment == null || comment.isBlank() ? null : comment.trim();
        this.ratedBy = by;
        this.status = ContractStatus.COMPLETED;
        touch(now);
    }

    public void terminate(String reason, Instant now) {
        requireStatus(ContractStatus.ACTIVE, "be terminated");
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("Give the reason for terminating the contract");
        }
        status = ContractStatus.TERMINATED;
        endedReason = reason.trim();
        touch(now);
    }

    /** End of the defects liability period, after which retention may be released. */
    public LocalDate defectsLiabilityEnds() {
        return completedAt == null ? null : completedAt.plusDays(defectsLiabilityDays);
    }

    public boolean isOpenForPayments() {
        return status == ContractStatus.ACTIVE || status == ContractStatus.COMPLETED
                || status == ContractStatus.TERMINATED;
    }

    public void changed(Instant now) {
        touch(now);
    }

    public void requireStatus(ContractStatus expected, String verb) {
        if (status != expected) {
            throw new BusinessRuleException("A contract that is " + status.name().toLowerCase() + " cannot " + verb);
        }
    }

    public String getNumber() { return number; }
    public UUID getTenderId() { return tenderId; }
    public UUID getBidId() { return bidId; }
    public UUID getSupplierId() { return supplierId; }
    public UUID getProjectId() { return projectId; }
    public UUID getRequisitionId() { return requisitionId; }
    public String getTitle() { return title; }
    public ContractStatus getStatus() { return status; }
    public String getCurrency() { return currency; }
    public long getOriginalValueMinor() { return originalValueMinor; }
    public long getCurrentValueMinor() { return currentValueMinor; }
    public int getRetentionBp() { return retentionBp; }
    public int getDefectsLiabilityDays() { return defectsLiabilityDays; }
    public long getPerformanceSecurityMinor() { return performanceSecurityMinor; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getOriginalEndDate() { return originalEndDate; }
    public LocalDate getEndDate() { return endDate; }
    public UUID getContractManagerId() { return contractManagerId; }
    public Instant getStandstillEndsAt() { return standstillEndsAt; }
    public ReviewStatus getReviewStatus() { return reviewStatus; }
    public String getReviewNote() { return reviewNote; }
    public LocalDate getSignedAt() { return signedAt; }
    public String getSupplierSignatory() { return supplierSignatory; }
    public String getEntitySignatory() { return entitySignatory; }
    public UUID getSignedRecordedBy() { return signedRecordedBy; }
    public LocalDate getCompletedAt() { return completedAt; }
    public String getEndedReason() { return endedReason; }
    public Integer getRatingQuality() { return ratingQuality; }
    public Integer getRatingTimeliness() { return ratingTimeliness; }
    public Integer getRatingCompliance() { return ratingCompliance; }
    public String getRatingComment() { return ratingComment; }
    public UUID getRatedBy() { return ratedBy; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
