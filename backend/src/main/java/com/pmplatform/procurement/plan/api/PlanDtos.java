package com.pmplatform.procurement.plan.api;

import com.pmplatform.procurement.plan.DepartmentStatus;
import com.pmplatform.procurement.plan.LineStage;
import com.pmplatform.procurement.plan.PlanCategory;
import com.pmplatform.procurement.plan.PlanStatus;
import com.pmplatform.procurement.tender.ReservedGroup;
import com.pmplatform.procurement.tender.api.TenderDtos.Ref;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response bodies for the annual procurement plan API. */
public final class PlanDtos {

    private PlanDtos() {
    }

    // ---------------------------------------------------------------- requests

    /** copyDepartments: start with the departments (and heads) of the latest earlier plan. */
    public record CreatePlanRequest(@NotNull LocalDate fyStart, Boolean copyDepartments) {
    }

    public record SettingsRequest(@NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
            BigDecimal agpoTargetPct) {
    }

    public record DepartmentRequest(@NotBlank @Size(max = 120) String name, @NotNull UUID headId,
            @PositiveOrZero Long ceilingMinor) {
    }

    public record LineRequest(
            @NotNull UUID departmentId,
            @NotBlank @Size(max = 500) String description,
            @NotNull PlanCategory category,
            @NotNull @DecimalMin(value = "0.001", message = "must be more than zero")
            @Digits(integer = 11, fraction = 3) BigDecimal quantity,
            @NotBlank @Size(max = 32) String unit,
            @NotNull @PositiveOrZero Long estimateMinor,
            @NotBlank @Size(max = 40) String methodCode,
            @NotNull @Min(1) @Max(4) Integer quarter,
            @Size(max = 120) String sourceOfFunds,
            @Size(max = 60) String budgetCode,
            ReservedGroup reservedGroup,
            UUID projectId) {
    }

    public record NoteRequest(@Size(max = 2000) String note) {
    }

    // ---------------------------------------------------------------- responses

    public record PlanSummary(UUID id, String financialYear, LocalDate fyStart, LocalDate fyEnd, PlanStatus status,
            int revision, long totalMinor, int lineCount, int departmentCount, boolean current) {
    }

    public record DepartmentView(UUID id, String name, Ref head, Long ceilingMinor, DepartmentStatus status,
            String returnNote, Ref submittedBy, Instant submittedAt, long totalMinor, int lineCount,
            boolean overCeiling, boolean canEditLines, boolean canSubmit, boolean canReturn, boolean canEdit,
            boolean canDelete, long version) {
    }

    public record ProjectRef(UUID id, String code, String name) {
    }

    public record LineView(UUID id, int lineNo, String reference, UUID departmentId, String department,
            String description, PlanCategory category, BigDecimal quantity, String unit, long estimateMinor,
            String methodCode, int quarter, String sourceOfFunds, String budgetCode, ReservedGroup reservedGroup,
            ProjectRef project, Long approvedEstimateMinor, String change, boolean cancelled, String cancelNote,
            long committedMinor, long balanceMinor, int requisitions, long awardedMinor, LineStage stage,
            boolean behind, boolean canEdit, boolean canDelete, boolean canCancel, long version) {
    }

    public record Bucket(String key, long plannedMinor, long committedMinor, int lines) {
    }

    public record Summary(long totalMinor, long approvedTotalMinor, int lineCount, long committedMinor,
            long awardedMinor, long agpoMinor, BigDecimal agpoPct, BigDecimal agpoTargetPct, int behindCount,
            int unplannedCount, long unplannedMinor, List<Bucket> byQuarter, List<Bucket> byCategory,
            List<Bucket> byMethod) {
    }

    public record RevisionView(int revision, Ref approvedBy, Instant approvedAt, long totalMinor, int lineCount,
            String reason, String note) {
    }

    public record UnplannedView(UUID requisitionId, String number, String title, long amountMinor, String reason,
            String status, Instant submittedAt) {
    }

    public record Permissions(boolean canManage, boolean canSubmit, boolean canDecide, boolean canRevise,
            boolean canAddDepartment) {
    }

    public record PlanView(UUID id, String financialYear, LocalDate fyStart, LocalDate fyEnd, String currency,
            PlanStatus status, int revision, BigDecimal agpoTargetPct, String revisionReason, String returnNote,
            Ref submittedBy, Instant submittedAt, Ref createdBy, Instant createdAt, long version,
            int currentQuarter, List<DepartmentView> departments, List<LineView> lines, Summary summary,
            List<RevisionView> revisions, List<UnplannedView> unplanned, List<String> warnings,
            Permissions permissions) {
    }

    public record ImportResult(int imported, List<String> errors, PlanView plan) {
    }

    /** An approved line a requisition can draw on. */
    public record AvailableLine(UUID id, String reference, String financialYear, String description,
            String department, PlanCategory category, String methodCode, int quarter, UUID projectId,
            long approvedMinor, long committedMinor, long balanceMinor) {
    }
}
