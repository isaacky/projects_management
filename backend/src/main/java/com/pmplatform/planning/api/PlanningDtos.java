package com.pmplatform.planning.api;

import com.pmplatform.planning.DependencyType;
import com.pmplatform.procurement.tender.api.TenderDtos.Ref;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response bodies for project schedules. */
public final class PlanningDtos {

    private PlanningDtos() {
    }

    // ---------------------------------------------------------------- requests

    /** {@code key} refers to another row of the same request (its key). */
    public record PredecessorInput(@NotBlank @Size(max = 64) String key, @NotNull DependencyType type,
            @Min(-365) @Max(365) Integer lagDays) {
    }

    /**
     * One row. {@code key}: an existing activity's id to keep it, or any new unique text for a new
     * row. Parents must come before their children.
     */
    public record ActivityInput(@NotBlank @Size(max = 64) String key, @Size(max = 64) String parentKey,
            @NotBlank @Size(max = 200) String name, @Size(max = 2000) String description,
            @NotNull @Min(0) @Max(3650) Integer durationDays, LocalDate earliestStart,
            @DecimalMin("0") @DecimalMax("99999999") BigDecimal weight, UUID ownerId,
            @Size(max = 50) List<@Valid PredecessorInput> predecessors) {
    }

    public record SaveScheduleRequest(@NotNull LocalDate startDate, Long version,
            @NotNull @Size(max = 3000) List<@Valid ActivityInput> activities) {
    }

    public record BaselineRequest(@Size(max = 1000) String note) {
    }

    public record ProgressRequest(LocalDate actualStart, LocalDate actualFinish,
            @DecimalMin("0") @DecimalMax("100") BigDecimal percent, @Size(max = 1000) String note) {
    }

    // ---------------------------------------------------------------- responses

    public record PredecessorView(UUID id, String wbs, String name, DependencyType type, int lagDays) {
    }

    public record ActivityView(UUID id, UUID parentId, String wbs, int depth, boolean summary, String name,
            String description, int durationDays, LocalDate earliestStart, BigDecimal weight, Ref owner,
            LocalDate start, LocalDate finish, int totalFloat, boolean critical, LocalDate baselineStart,
            LocalDate baselineFinish, LocalDate forecastStart, LocalDate forecastFinish, LocalDate actualStart,
            LocalDate actualFinish, BigDecimal percent, String progressNote, Instant progressUpdatedAt,
            Ref progressUpdatedBy, boolean late, boolean canUpdateProgress, List<PredecessorView> predecessors) {
    }

    public record CurvePoint(LocalDate date, BigDecimal planned, BigDecimal earned) {
    }

    public record Metrics(LocalDate startDate, LocalDate plannedFinish, LocalDate baselineFinish,
            LocalDate forecastFinish, int slipDays, BigDecimal plannedPct, BigDecimal earnedPct, BigDecimal spi,
            int activities, int criticalActivities, int lateActivities) {
    }

    public record Permissions(boolean canEdit, boolean canBaseline, boolean canImport) {
    }

    public record ScheduleView(boolean exists, UUID projectId, LocalDate startDate, Instant baselineSetAt,
            Ref baselineSetBy, String baselineNote, int baselineCount, long version, List<ActivityView> activities,
            Metrics metrics, List<CurvePoint> plannedCurve, List<CurvePoint> actualCurve, List<String> notices,
            Permissions permissions) {
    }
}
