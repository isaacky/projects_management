package com.pmplatform.procurement.tender.api;

import com.pmplatform.procurement.tender.BidStatus;
import com.pmplatform.procurement.evaluation.EvaluationStatus;
import com.pmplatform.procurement.tender.Envelope;
import com.pmplatform.procurement.tender.EvaluationStage;
import com.pmplatform.procurement.tender.ReservedGroup;
import com.pmplatform.procurement.tender.TenderStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Request and response bodies for tenders, bids and openings. */
public final class TenderDtos {

    private TenderDtos() {
    }

    // ---------------------------------------------------------------- requests

    public record CreateTenderRequest(
            @NotNull UUID requisitionId,
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]*") String methodCode,
            @Size(max = 200) String title,
            @Size(max = 4000) String description,
            ReservedGroup reservedGroup) {
    }

    public record EditTenderRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 4000) String description,
            Instant closingAt,
            ReservedGroup reservedGroup) {
    }

    public record InviteRequest(@NotEmpty Set<UUID> supplierIds) {
    }

    public record ExtendRequest(@NotNull Instant closingAt, @NotBlank @Size(max = 1000) String reason) {
    }

    public record ReasonRequest(@NotBlank @Size(max = 2000) String reason) {
    }

    public record OpeningRequest(
            @NotNull Envelope envelope,
            @NotEmpty Set<UUID> attendeeIds,
            @Size(max = 200) String venue,
            @Size(max = 2000) String notes) {
    }

    public record ReadOutRequest(@PositiveOrZero Long priceMinor, @Size(max = 1000) String note) {
    }

    // ---------------------------------------------------------------- responses

    public record Ref(UUID id, String name) {
    }

    public record DocumentResponse(UUID id, String fileName, String contentType, long sizeBytes, String sha256,
            Ref uploadedBy, Instant uploadedAt) {
    }

    /** A sealed file: name and size are shown only once its envelope is open. */
    public record BidFileResponse(UUID id, Envelope envelope, boolean sealed, String fileName, Long sizeBytes,
            String sha256, boolean addedAfterOpening, Instant uploadedAt) {
    }

    public record EnvelopeSummary(Envelope envelope, int fileCount, boolean sealed) {
    }

    public record BidResponse(UUID id, Ref supplier, String supplierKraPin, BidStatus status, String receiptNo,
            Instant receivedAt, Ref receivedBy, String deliveryNote, String receiptHash, Instant withdrawnAt,
            String withdrawReason, List<EnvelopeSummary> envelopes, List<BidFileResponse> files,
            Long readOutPriceMinor, String readOutNote, EvaluationStage disqualifiedStage,
            String disqualifiedReason) {
    }

    public record OpeningResponse(UUID id, Envelope envelope, Instant openedAt, Ref openedBy, String venue,
            String notes, List<Ref> attendees) {
    }

    public record Permissions(boolean canEdit, boolean canPublish, boolean canInvite, boolean canExtend,
            boolean canCancel, boolean canRecordBids, boolean canOpen, boolean canReadOut, boolean canAddScans,
            boolean canReadBidFiles) {
    }

    public record TenderSummary(UUID id, String number, String title, String methodCode, TenderStatus status,
            boolean closed, Instant closingAt, long estimatedValueMinor, String currency, String projectCode,
            String requisitionNumber, long bidCount, ReservedGroup reservedGroup) {
    }

    public record TenderDetail(UUID id, String number, String title, String description, TenderStatus status,
            boolean acceptingBids, boolean closed, String methodCode, String methodName, boolean twoEnvelope,
            boolean invitedOnly, int minBidPeriodDays, int openingQuorum, Instant earliestClosingIfPublishedNow,
            Instant closingAt, Instant publishedAt, String cancelledReason, String currency,
            long estimatedValueMinor, Ref project, String projectCode, Ref requisition, String requisitionNumber,
            Ref createdBy, Instant createdAt, long version, Envelope nextEnvelopeToOpen,
            List<DocumentResponse> documents, List<Ref> invitedSuppliers, List<BidResponse> bids,
            List<OpeningResponse> openings, Instant serverTime, Permissions permissions,
            ReservedGroup reservedGroup, EvaluationStatus evaluationStatus, String openingBlockedReason,
            UUID awardedBidId, Long awardedAmountMinor, Instant awardedAt, Instant standstillEndsAt) {
    }
}
