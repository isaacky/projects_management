package com.pmplatform.closeout.api;

import com.pmplatform.closeout.CloseoutDocumentKind;
import com.pmplatform.closeout.CloseoutStatus;
import com.pmplatform.closeout.DefectSeverity;
import com.pmplatform.closeout.DefectStatus;
import com.pmplatform.closeout.ItemStatus;
import com.pmplatform.closeout.LessonCategory;
import com.pmplatform.closeout.LessonKind;
import com.pmplatform.closeout.LessonStatus;
import com.pmplatform.portfolio.ProjectPhase;
import com.pmplatform.procurement.tender.api.TenderDtos.Ref;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response bodies for closeout, handover and lessons learned. */
public final class CloseoutDtos {

    private CloseoutDtos() {
    }

    // ---------------------------------------------------------------- requests

    public record ItemRequest(@NotBlank @Size(max = 200) String title, @Size(max = 1000) String description,
            @Size(max = 64) String responsibleRole, Boolean evidenceRequired) {
    }

    public record NoteRequest(@Size(max = 2000) String note) {
    }

    public record AssetInput(@NotBlank @Size(max = 300) String description,
            @NotNull @DecimalMin(value = "0.001", message = "must be more than zero")
            @Digits(integer = 11, fraction = 3) BigDecimal quantity,
            @NotBlank @Size(max = 32) String unit, @Size(max = 120) String tag, @Size(max = 200) String location,
            @PositiveOrZero Long valueMinor) {
    }

    public record HandoverRequest(@Size(max = 120) String receivingUnit, UUID receivingOfficerId, LocalDate handoverDate,
            @Size(max = 4000) String note, @NotNull @Size(max = 500) List<@Valid AssetInput> assets) {
    }

    public record DefectRequest(UUID contractId, @NotBlank @Size(max = 200) String title,
            @Size(max = 4000) String description, @Size(max = 200) String location, @NotNull DefectSeverity severity,
            LocalDate dueDate) {
    }

    public record LessonRequest(@NotNull LessonKind kind, @NotNull LessonCategory category, ProjectPhase phase,
            @NotBlank @Size(max = 200) String title, @NotBlank @Size(max = 4000) String whatHappened,
            @Size(max = 4000) String recommendation) {
    }

    public record ReviewRequest(@NotNull LessonStatus status, @Size(max = 2000) String note) {
    }

    public record TemplateRequest(@NotNull @Size(max = 100) List<@Valid ItemRequest> items) {
    }

    // ---------------------------------------------------------------- responses

    public record DocumentView(UUID id, CloseoutDocumentKind kind, UUID refId, String fileName, String contentType,
            long sizeBytes, String sha256, Ref uploadedBy, Instant uploadedAt, boolean canDelete) {
    }

    public record ItemView(UUID id, int sortOrder, String title, String description, String responsibleRole,
            boolean evidenceRequired, ItemStatus status, String note, Ref doneBy, Instant doneAt,
            List<DocumentView> evidence, boolean canMark, boolean canEdit, long version) {
    }

    /** A condition the platform checks itself (contracts finished, schedule complete, no critical defects). */
    public record AutoCheck(String key, String label, boolean passed, String detail) {
    }

    public record DefectView(UUID id, int number, String title, String description, String location,
            DefectSeverity severity, DefectStatus status, UUID contractId, String contractNumber, LocalDate dueDate,
            boolean overdue, Ref reportedBy, Instant reportedAt, Ref rectifiedBy, Instant rectifiedAt,
            String rectifiedNote, Ref closedBy, Instant closedAt, String closedNote, List<DocumentView> files,
            boolean canEdit, boolean canRectify, boolean canVerify, boolean canWaive, long version) {
    }

    public record AssetView(UUID id, int sortOrder, String description, BigDecimal quantity, String unit, String tag,
            String location, Long valueMinor) {
    }

    public record HandoverView(String receivingUnit, Ref receivingOfficer, LocalDate handoverDate, String note,
            List<AssetView> assets, long assetValueMinor, List<DocumentView> documents, Ref submittedBy,
            Instant submittedAt, Ref decidedBy, Instant decidedAt, String decisionNote) {
    }

    public record ContractLine(UUID id, String number, String title, String status, long valueMinor,
            long paidMinor, long retentionHeldMinor, LocalDate defectsLiabilityEnds) {
    }

    /** Final figures: live until the handover is accepted, then frozen. */
    public record Figures(boolean frozen, long budgetMinor, long contractValueMinor, long certifiedMinor,
            long paidMinor, long varianceMinor, LocalDate baselineFinish, LocalDate actualFinish, Integer slipDays,
            BigDecimal earnedPct, LocalDate dlpEnd, List<ContractLine> contracts, String currency) {
    }

    public record LessonView(UUID id, UUID projectId, String projectCode, String projectName, LessonKind kind,
            LessonCategory category, ProjectPhase phase, String title, String whatHappened, String recommendation,
            LessonStatus status, String reviewNote, Ref reviewedBy, Instant reviewedAt, Ref createdBy,
            Instant createdAt, boolean canEdit, boolean canReview, long version) {
    }

    public record Permissions(boolean canStart, boolean canEdit, boolean canSubmitHandover,
            boolean canDecideHandover, boolean canClose, boolean canReportDefect, boolean canAddLesson) {
    }

    public record CloseoutView(boolean exists, UUID projectId, ProjectPhase phase, CloseoutStatus status,
            Ref startedBy, Instant startedAt, List<ItemView> checklist, List<AutoCheck> autoChecks,
            List<DefectView> defects, HandoverView handover, Figures figures, List<LessonView> lessons,
            List<String> handoverBlockers, List<String> closeBlockers, List<String> warnings, Ref closedBy,
            Instant closedAt, String closureNote, Permissions permissions, long version) {
    }

    public record TemplateItemView(UUID id, int sortOrder, String title, String description, String responsibleRole,
            boolean evidenceRequired) {
    }

    /** usingDefaults: the organisation has not set up its own list, so the built-in one is used. */
    public record TemplateView(boolean usingDefaults, List<TemplateItemView> items, boolean canEdit) {
    }
}
