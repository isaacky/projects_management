package com.pmplatform.execution.api;

import com.pmplatform.execution.ExecutionRules.RiskLevel;
import com.pmplatform.execution.IssuePriority;
import com.pmplatform.execution.IssueStatus;
import com.pmplatform.execution.Rag;
import com.pmplatform.execution.ReportStatus;
import com.pmplatform.execution.RiskCategory;
import com.pmplatform.execution.RiskResponse;
import com.pmplatform.execution.RiskStatus;
import com.pmplatform.portfolio.ProjectPhase;
import com.pmplatform.procurement.tender.api.TenderDtos.Ref;
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

/** Request and response bodies for risks, issues, status reports and portfolio health. */
public final class ExecutionDtos {

    private ExecutionDtos() {
    }

    // ---------------------------------------------------------------- risks

    public record RiskRequest(@NotBlank @Size(max = 200) String title, @Size(max = 2000) String description,
            @NotNull RiskCategory category, @NotNull @Min(1) @Max(5) Integer probability,
            @NotNull @Min(1) @Max(5) Integer impact, @NotNull RiskResponse response,
            @Size(max = 4000) String mitigation, UUID ownerId, LocalDate dueDate) {
    }

    public record NoteRequest(@Size(max = 2000) String note) {
    }

    public record RiskView(UUID id, int number, String title, String description, RiskCategory category,
            int probability, int impact, int score, RiskLevel level, RiskResponse response, String mitigation,
            Ref owner, LocalDate dueDate, boolean overdue, RiskStatus status, String closedNote, Instant closedAt,
            Ref createdBy, Instant createdAt, boolean canEdit) {
    }

    // ---------------------------------------------------------------- issues

    public record IssueRequest(@NotBlank @Size(max = 200) String title, @Size(max = 2000) String description,
            @NotNull IssuePriority priority, IssueStatus status, UUID ownerId, LocalDate dueDate,
            @Size(max = 2000) String resolution, UUID riskId) {
    }

    public record IssueView(UUID id, int number, String title, String description, IssuePriority priority,
            IssueStatus status, Ref owner, LocalDate dueDate, boolean overdue, String resolution, UUID riskId,
            Integer riskNumber, Ref createdBy, Instant createdAt, boolean canEdit) {
    }

    // ---------------------------------------------------------------- status reports

    public record CreateReportRequest(@NotNull LocalDate periodEnd) {
    }

    public record ReportRequest(@NotNull LocalDate periodEnd, @NotNull Rag overall, @NotNull Rag schedule,
            @NotNull Rag cost, @NotNull Rag scope, @NotNull Rag risk, @NotBlank @Size(max = 4000) String summary,
            @Size(max = 4000) String achievements, @Size(max = 4000) String nextSteps,
            @Size(max = 4000) String escalations) {
    }

    public record Figures(BigDecimal plannedPct, BigDecimal earnedPct, BigDecimal spi, BigDecimal cpi,
            LocalDate baselineFinish, LocalDate forecastFinish, Integer slipDays, long budgetMinor,
            long committedMinor, long certifiedMinor, int openRisks, int highRisks, int criticalRisks,
            int openIssues, boolean frozen) {
    }

    public record Suggested(Rag overall, Rag schedule, Rag cost, Rag risk) {
    }

    public record ReportView(UUID id, LocalDate periodEnd, ReportStatus status, Rag overall, Rag schedule, Rag cost,
            Rag scope, Rag risk, String summary, String achievements, String nextSteps, String escalations,
            Figures figures, Suggested suggested, Ref createdBy, Instant createdAt, Ref submittedBy,
            Instant submittedAt, Ref reviewedBy, Instant reviewedAt, String reviewComment, boolean canEdit,
            boolean canSubmit, boolean canReview) {
    }

    // ---------------------------------------------------------------- portfolio

    public record ProjectHealth(UUID projectId, String code, String name, ProjectPhase phase, String currency,
            long budgetMinor, long committedMinor, long certifiedMinor, BigDecimal plannedPct, BigDecimal earnedPct,
            BigDecimal spi, BigDecimal cpi, LocalDate baselineFinish, LocalDate forecastFinish, Integer slipDays,
            int openRisks, int highRisks, int openIssues, Rag lastOverall, LocalDate lastReportPeriod,
            Rag suggestedOverall) {
    }
}
