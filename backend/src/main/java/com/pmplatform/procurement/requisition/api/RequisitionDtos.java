package com.pmplatform.procurement.requisition.api;

import com.pmplatform.procurement.plan.PlanCoverage;
import com.pmplatform.procurement.plan.api.PlanDtos.AvailableLine;
import com.pmplatform.procurement.requisition.ApprovalStepStatus;
import com.pmplatform.procurement.requisition.RequisitionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response bodies for the requisitions API. */
public final class RequisitionDtos {

    private RequisitionDtos() {
    }

    // ---------------------------------------------------------------- requests

    public record LineRequest(
            @NotBlank @Size(max = 500) String description,
            @NotNull @DecimalMin(value = "0.001", message = "must be more than zero")
            @Digits(integer = 11, fraction = 3) BigDecimal quantity,
            @NotBlank @Size(max = 32) String unit,
            @PositiveOrZero long unitPriceMinor) {
    }

    public record RequisitionRequest(
            @NotNull UUID projectId,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 4000) String justification,
            @Size(max = 120) String department,
            LocalDate neededBy,
            @Pattern(regexp = "[A-Z]{3}") String currency,
            @Pattern(regexp = "[A-Z][A-Z0-9_]*") String proposedMethodCode,
            @NotNull @Size(max = 200) List<@Valid LineRequest> lines,
            UUID planLineId,
            @Size(max = 2000) String unplannedReason) {

        public String currencyOrDefault() {
            return currency == null || currency.isBlank() ? "KES" : currency;
        }
    }

    public record DecisionRequest(@Size(max = 2000) String comment) {
    }

    // ---------------------------------------------------------------- responses

    public record PersonRef(UUID id, String displayName) {
    }

    public record ProjectRef(UUID id, String code, String name) {
    }

    public record LineResponse(UUID id, int lineNo, String description, BigDecimal quantity, String unit,
            long unitPriceMinor, long totalMinor) {
    }

    public record ApprovalResponse(UUID id, int round, int stepOrder, String approverRole,
            ApprovalStepStatus status, PersonRef decidedBy, Instant decidedAt, String comment) {
    }

    /** What the current user may do with this requisition, so the UI shows only valid buttons. */
    public record Permissions(boolean canEdit, boolean canSubmit, boolean canDecide, boolean canCancel) {
    }

    public record RequisitionSummary(UUID id, String number, String title, ProjectRef project,
            RequisitionStatus status, long estimatedTotalMinor, String currency, PersonRef requestedBy,
            String pendingRole, Instant createdAt, Instant submittedAt) {
    }

    public record RequisitionDetail(UUID id, String number, String title, String justification,
            String department, LocalDate neededBy, ProjectRef project, RequisitionStatus status,
            String currency, long estimatedTotalMinor, String proposedMethodCode, PersonRef requestedBy,
            int currentRound, Instant createdAt, Instant submittedAt, Instant decidedAt, Instant updatedAt,
            long version, List<LineResponse> lines, List<ApprovalResponse> approvals,
            Permissions permissions, AvailableLine planLine, String unplannedReason, PlanCoverage planStatus) {
    }
}
