package com.pmplatform.procurement.evaluation.api;

import com.pmplatform.procurement.config.CriterionStage;
import com.pmplatform.procurement.config.ReportSignatories;
import com.pmplatform.procurement.evaluation.ApprovalStatus;
import com.pmplatform.procurement.evaluation.CommitteeRole;
import com.pmplatform.procurement.evaluation.EvaluationStatus;
import com.pmplatform.procurement.supplier.AgpoCategory;
import com.pmplatform.procurement.tender.EvaluationStage;
import com.pmplatform.procurement.tender.api.TenderDtos.Ref;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request and response bodies for bid evaluation and award. */
public final class EvaluationDtos {

    private EvaluationDtos() {
    }

    // ---------------------------------------------------------------- requests

    public record SetUpRequest(@Pattern(regexp = "[A-Z][A-Z0-9_]*") String templateCode) {
    }

    public record CriterionRequest(
            @NotNull CriterionStage stage,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @DecimalMin("0.01") @DecimalMax("9999.99") BigDecimal maxScore) {
    }

    public record CriteriaRequest(
            @Pattern(regexp = "[A-Z][A-Z0-9_]*") String templateCode,
            @Min(0) @Max(100) Integer passMarkPct,
            @NotEmpty @Size(max = 60) List<@Valid CriterionRequest> criteria) {
    }

    public record MemberRequest(@NotNull UUID userId, @NotNull CommitteeRole role) {
    }

    public record CommitteeRequest(@NotEmpty @Size(max = 30) List<@Valid MemberRequest> members) {
    }

    /** After appointment: the accounting officer adds a member or names a new chair. */
    public record CommitteeChangeRequest(@NotNull UUID userId, @NotNull CommitteeRole role,
            @NotBlank @Size(max = 1000) String reason) {
    }

    public record DeclarationRequest(@NotNull Boolean conflict, @Size(max = 2000) String declaration) {
    }

    public record MarkRequest(@NotNull UUID criterionId, Boolean passed,
            @DecimalMin("0") @DecimalMax("9999.99") BigDecimal score, @Size(max = 1000) String comment) {
    }

    public record MarksRequest(@NotNull UUID bidId, @NotEmpty @Size(max = 100) List<@Valid MarkRequest> marks) {
    }

    public record FinancialRequest(@NotNull @PositiveOrZero Long evaluatedPriceMinor, @Size(max = 2000) String note) {
    }

    public record ReasonRequest(@NotBlank @Size(max = 2000) String reason) {
    }

    public record ReportRequest(UUID bidId, Boolean noAward,
            @NotBlank @Size(max = 2800) String recommendation,
            @Size(max = 1000) String deviationReason) {
    }

    public record SignRequest(@Size(max = 2000) String note) {
    }

    public enum Decision { APPROVE, RETURN, TERMINATE }

    public record DecisionRequest(@NotNull Decision decision, @Size(max = 2000) String note) {
    }

    // ---------------------------------------------------------------- responses

    public record TemplateOption(String code, String name, String methodCode, boolean suggested) {
    }

    public record MemberView(UUID userId, String name, CommitteeRole role, Instant declaredAt, Boolean hasConflict,
            String declaration, boolean signedThisRound, Instant signedAt, String signNote,
            int marksDone, int marksNeeded) {
    }

    public record CriterionView(UUID id, CriterionStage stage, int sortOrder, String name, String description,
            BigDecimal maxScore) {
    }

    public record BidRow(UUID bidId, String receiptNo, Ref supplier, String kraPin, AgpoCategory agpoCategory,
            boolean inCurrentStage, EvaluationStage disqualifiedStage, String disqualifiedReason,
            Boolean preliminaryPassed, String preliminaryNote, BigDecimal technicalScore, BigDecimal technicalPct,
            Boolean technicalPassed, Long readOutPriceMinor, Long evaluatedPriceMinor, String priceNote,
            Integer rank) {
    }

    public record MarkView(UUID criterionId, UUID bidId, UUID memberId, Boolean passed, BigDecimal score,
            String comment) {
    }

    public record StageProgress(EvaluationStatus stage, int bids, int criteria, int scoringMembers,
            int completeMembers, int quorum, boolean readyToClose, String waitingFor) {
    }

    public record Recommendation(boolean noAward, UUID bidId, Long amountMinor, String text, Ref submittedBy,
            Instant submittedAt, int round) {
    }

    public record ApprovalView(UUID id, int round, int stepOrder, String approverRole, boolean opinion,
            ApprovalStatus status, Ref decidedBy, Instant decidedAt, String note) {
    }

    public record Award(UUID bidId, Ref supplier, Long amountMinor, Instant awardedAt, Instant standstillEndsAt) {
    }

    public record Me(CommitteeRole role, boolean cleared, boolean mustDeclare, boolean mayScore, boolean chair,
            boolean mustSign) {
    }

    public record Permissions(boolean canEditSetup, boolean canAppoint, boolean canChangeCommittee,
            boolean canDeclare, boolean canScore, boolean canCloseStage, boolean canEditFinancial,
            boolean canSubmitReport, boolean canSign, boolean canDecide, boolean decisionIsOpinion,
            boolean decisionIsFinal) {
    }

    public record EvaluationDetail(UUID id, UUID tenderId, EvaluationStatus status, int round, String templateCode,
            Integer passMarkPct, int minMembers, int maxMembers, int quorum, boolean requiresCoiDeclaration,
            ReportSignatories reportSignatories, String reportReviewerRole, Ref createdBy, Instant createdAt,
            Ref appointedBy, Instant appointedAt, Instant preliminaryClosedAt, Instant technicalClosedAt,
            Instant financialClosedAt, Instant decidedAt, List<MemberView> members, List<CriterionView> criteria,
            List<BidRow> bids, List<MarkView> myMarks, List<MarkView> closedStageMarks, StageProgress progress,
            Recommendation recommendation, List<Ref> signatories, List<ApprovalView> approvals, Award award,
            long budgetMinor, String currency, List<String> warnings, Me me, Permissions permissions,
            long version) {
    }

    /** The evaluation of a tender, or what is needed to set one up. */
    public record EvaluationResponse(boolean exists, boolean canSetUp, String setUpBlockedReason,
            List<TemplateOption> templates, EvaluationDetail evaluation) {
    }
}
