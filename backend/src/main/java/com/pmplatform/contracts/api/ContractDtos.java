package com.pmplatform.contracts.api;

import com.pmplatform.contracts.ContractStatus;
import com.pmplatform.contracts.DocumentKind;
import com.pmplatform.contracts.InvoiceKind;
import com.pmplatform.contracts.InvoiceStatus;
import com.pmplatform.contracts.MilestoneStatus;
import com.pmplatform.contracts.ReviewStatus;
import com.pmplatform.contracts.SecurityForm;
import com.pmplatform.contracts.SecurityKind;
import com.pmplatform.contracts.SecurityStatus;
import com.pmplatform.contracts.StepStatus;
import com.pmplatform.contracts.VariationStatus;
import com.pmplatform.procurement.tender.api.TenderDtos.Ref;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response bodies for contracts. */
public final class ContractDtos {

    private ContractDtos() {
    }

    // ---------------------------------------------------------------- requests

    public record CreateContractRequest(@NotNull UUID tenderId, @Size(max = 200) String title) {
    }

    public record EditContractRequest(
            @NotBlank @Size(max = 200) String title,
            LocalDate startDate,
            LocalDate endDate,
            @Min(0) @Max(2000) Integer retentionBp,
            @Min(0) @Max(3650) Integer defectsLiabilityDays,
            @PositiveOrZero Long performanceSecurityMinor,
            UUID contractManagerId) {
    }

    public record MilestoneRequest(UUID id, @NotBlank @Size(max = 200) String title,
            @Size(max = 1000) String description, LocalDate dueDate, @NotNull @PositiveOrZero Long amountMinor) {
    }

    public record ScheduleRequest(@NotNull @Size(max = 100) List<@Valid MilestoneRequest> milestones) {
    }

    public record ReviewRequest(@NotNull ReviewStatus status, @NotBlank @Size(max = 2000) String note) {
    }

    public record ReasonRequest(@NotBlank @Size(max = 2000) String reason) {
    }

    public record SignRequest(@NotNull LocalDate signedOn, @NotBlank @Size(max = 200) String supplierSignatory,
            @NotBlank @Size(max = 200) String entitySignatory) {
    }

    public record ExtendSecurityRequest(@NotNull LocalDate expiryDate) {
    }

    public record CloseSecurityRequest(@NotNull SecurityStatus outcome, @NotBlank @Size(max = 1000) String note) {
    }

    public record AcceptMilestoneRequest(@NotNull LocalDate acceptedOn, @NotBlank @Size(max = 2000) String note) {
    }

    public record CertifyRequest(@NotBlank @Size(max = 2000) String note) {
    }

    public record PayRequest(@NotNull LocalDate paidOn, @NotBlank @Size(max = 100) String paymentRef) {
    }

    public record VariationRequest(Long valueChangeMinor, LocalDate newEndDate,
            @NotBlank @Size(max = 2000) String reason) {
    }

    public record VariationDecisionRequest(@NotNull Boolean approve, @Size(max = 2000) String note) {
    }

    public record CompleteRequest(@NotNull LocalDate completedOn, @NotNull @Min(1) @Max(5) Integer quality,
            @NotNull @Min(1) @Max(5) Integer timeliness, @NotNull @Min(1) @Max(5) Integer compliance,
            @Size(max = 2000) String comment) {
    }

    // ---------------------------------------------------------------- responses

    public record Totals(long invoicedMinor, long certifiedMinor, long paidMinor, long retentionHeldMinor,
            long awaitingPaymentMinor) {
    }

    public record ContractSummary(UUID id, String number, String title, ContractStatus status, Ref supplier,
            UUID projectId, String projectCode, String tenderNumber, String currency, long originalValueMinor,
            long currentValueMinor, long certifiedMinor, long paidMinor, LocalDate endDate, Ref contractManager,
            long retentionHeldMinor, LocalDate defectsLiabilityEnds) {
    }

    public record MilestoneView(UUID id, int sortOrder, String title, String description, LocalDate dueDate,
            long amountMinor, MilestoneStatus status, LocalDate acceptedAt, Ref acceptedBy, String acceptanceNote,
            long invoicedMinor, boolean overdue) {
    }

    public record SecurityView(UUID id, SecurityKind kind, SecurityForm form, long amountMinor, String issuer,
            String reference, LocalDate expiryDate, SecurityStatus status, LocalDate receivedAt, String closedNote,
            boolean expiringSoon) {
    }

    public record InvoiceView(UUID id, InvoiceKind kind, int certificateNo, String supplierRef, LocalDate invoiceDate,
            UUID milestoneId, String milestoneTitle, long grossMinor, long retentionMinor, long netMinor,
            InvoiceStatus status, Ref receivedBy, Instant receivedAt, Ref certifiedBy, Instant certifiedAt,
            String certificateNote, Ref approvedBy, Instant approvedAt, Ref paidBy, LocalDate paidOn,
            String paymentRef, String rejectedReason, boolean canCertify, boolean canApprove, boolean canPay,
            boolean canReject) {
    }

    public record StepView(int stepOrder, String approverRole, StepStatus status, Ref decidedBy, Instant decidedAt,
            String note) {
    }

    public record VariationView(UUID id, int number, long valueChangeMinor, LocalDate newEndDate, String reason,
            VariationStatus status, Ref requestedBy, Instant requestedAt, Instant decidedAt, List<StepView> steps,
            boolean canDecide) {
    }

    public record DocumentView(UUID id, DocumentKind kind, UUID refId, String fileName, long sizeBytes,
            String sha256, Ref uploadedBy, Instant uploadedAt) {
    }

    public record Permissions(boolean canEdit, boolean canSign, boolean canCancel, boolean canRecordReview,
            boolean canAddSecurity, boolean canManageSecurities, boolean canUploadDocuments, boolean canAcceptMilestones,
            boolean canEditSchedule, boolean canRecordInvoice, boolean canReleaseRetention, boolean canRequestVariation,
            boolean canComplete, boolean canTerminate) {
    }

    public record Rating(Integer quality, Integer timeliness, Integer compliance, String comment, Ref ratedBy) {
    }

    public record ContractDetail(UUID id, String number, String title, ContractStatus status, Ref supplier,
            String supplierKraPin, Ref project, String projectCode, UUID tenderId, String tenderNumber,
            UUID requisitionId, String requisitionNumber, String currency, long originalValueMinor,
            long currentValueMinor, int retentionBp, int defectsLiabilityDays, long performanceSecurityMinor,
            LocalDate startDate, LocalDate originalEndDate, LocalDate endDate, Ref contractManager,
            Instant standstillEndsAt, ReviewStatus reviewStatus, String reviewNote, LocalDate signedAt,
            String supplierSignatory, String entitySignatory, LocalDate completedAt, LocalDate defectsLiabilityEnds,
            String endedReason, Rating rating, Ref createdBy, Instant createdAt, long version,
            List<MilestoneView> milestones, long scheduledMinor, List<SecurityView> securities,
            List<InvoiceView> invoices, List<VariationView> variations, List<DocumentView> documents, Totals totals,
            long variationHeadroomMinor, List<String> signingProblems, List<String> warnings, Permissions permissions) {
    }

    public record SupplierPerformance(UUID supplierId, int contracts, int completed, Double averageRating,
            long totalValueMinor) {
    }
}
