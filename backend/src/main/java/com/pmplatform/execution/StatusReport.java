package com.pmplatform.execution;

import com.pmplatform.platform.persistence.TenantEntity;
import com.pmplatform.platform.web.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A periodic project status report. Figures are frozen when it is submitted, so later progress
 * never changes what was reported. DRAFT -> SUBMITTED -> REVIEWED (by the PMO).
 */
@Entity
@Table(schema = "execution", name = "status_reports")
public class StatusReport extends TenantEntity {

    /** The figures copied into the report when it is submitted. */
    public record Figures(BigDecimal plannedPct, BigDecimal earnedPct, BigDecimal spi, BigDecimal cpi,
            LocalDate baselineFinish, LocalDate forecastFinish, long budgetMinor, long committedMinor,
            Integer slipDays, long certifiedMinor, int openRisks, int highRisks, int criticalRisks, int openIssues) {
    }

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "rag_overall", nullable = false)
    private Rag ragOverall;

    @Enumerated(EnumType.STRING)
    @Column(name = "rag_schedule", nullable = false)
    private Rag ragSchedule;

    @Enumerated(EnumType.STRING)
    @Column(name = "rag_cost", nullable = false)
    private Rag ragCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "rag_scope", nullable = false)
    private Rag ragScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "rag_risk", nullable = false)
    private Rag ragRisk;

    @Column(nullable = false)
    private String summary;

    private String achievements;

    @Column(name = "next_steps")
    private String nextSteps;

    private String escalations;

    @Column(name = "planned_pct", precision = 5, scale = 2)
    private BigDecimal plannedPct;

    @Column(name = "earned_pct", precision = 5, scale = 2)
    private BigDecimal earnedPct;

    @Column(precision = 6, scale = 3)
    private BigDecimal spi;

    @Column(precision = 6, scale = 3)
    private BigDecimal cpi;

    @Column(name = "baseline_finish")
    private LocalDate baselineFinish;

    @Column(name = "forecast_finish")
    private LocalDate forecastFinish;

    @Column(name = "budget_minor")
    private Long budgetMinor;

    @Column(name = "committed_minor")
    private Long committedMinor;

    @Column(name = "certified_minor")
    private Long certifiedMinor;

    @Column(name = "open_risks")
    private Integer openRisks;

    @Column(name = "high_risks")
    private Integer highRisks;

    @Column(name = "critical_risks")
    private Integer criticalRisks;

    @Column(name = "slip_days")
    private Integer slipDays;

    @Column(name = "open_issues")
    private Integer openIssues;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_comment")
    private String reviewComment;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StatusReport() {
    }

    public StatusReport(UUID tenantId, UUID projectId, LocalDate periodEnd, UUID createdBy, Instant now) {
        super(tenantId, now);
        this.projectId = projectId;
        this.periodEnd = periodEnd;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.status = ReportStatus.DRAFT;
    }

    public void edit(LocalDate periodEnd, Rag overall, Rag schedule, Rag cost, Rag scope, Rag risk, String summary,
            String achievements, String nextSteps, String escalations, Instant now) {
        if (status != ReportStatus.DRAFT) {
            throw new BusinessRuleException("A submitted report cannot be changed");
        }
        if (summary == null || summary.isBlank()) {
            throw new BusinessRuleException("Write the summary");
        }
        this.periodEnd = periodEnd;
        this.ragOverall = overall;
        this.ragSchedule = schedule;
        this.ragCost = cost;
        this.ragScope = scope;
        this.ragRisk = risk;
        this.summary = summary.trim();
        this.achievements = blank(achievements);
        this.nextSteps = blank(nextSteps);
        this.escalations = blank(escalations);
        touch(now);
    }

    public void submit(Figures f, UUID by, Instant now) {
        if (status != ReportStatus.DRAFT) {
            throw new BusinessRuleException("This report was already submitted");
        }
        plannedPct = f.plannedPct();
        earnedPct = f.earnedPct();
        spi = f.spi();
        cpi = f.cpi();
        baselineFinish = f.baselineFinish();
        forecastFinish = f.forecastFinish();
        budgetMinor = f.budgetMinor();
        committedMinor = f.committedMinor();
        certifiedMinor = f.certifiedMinor();
        openRisks = f.openRisks();
        highRisks = f.highRisks();
        criticalRisks = f.criticalRisks();
        slipDays = f.slipDays();
        openIssues = f.openIssues();
        status = ReportStatus.SUBMITTED;
        submittedBy = by;
        submittedAt = now;
        touch(now);
    }

    public void review(UUID by, String comment, Instant now) {
        if (status != ReportStatus.SUBMITTED) {
            throw new BusinessRuleException(status == ReportStatus.DRAFT ? "The report has not been submitted"
                    : "The report was already reviewed");
        }
        status = ReportStatus.REVIEWED;
        reviewedBy = by;
        reviewedAt = now;
        reviewComment = blank(comment);
        touch(now);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public UUID getProjectId() { return projectId; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public ReportStatus getStatus() { return status; }
    public Rag getRagOverall() { return ragOverall; }
    public Rag getRagSchedule() { return ragSchedule; }
    public Rag getRagCost() { return ragCost; }
    public Rag getRagScope() { return ragScope; }
    public Rag getRagRisk() { return ragRisk; }
    public String getSummary() { return summary; }
    public String getAchievements() { return achievements; }
    public String getNextSteps() { return nextSteps; }
    public String getEscalations() { return escalations; }
    public BigDecimal getPlannedPct() { return plannedPct; }
    public BigDecimal getEarnedPct() { return earnedPct; }
    public BigDecimal getSpi() { return spi; }
    public BigDecimal getCpi() { return cpi; }
    public LocalDate getBaselineFinish() { return baselineFinish; }
    public LocalDate getForecastFinish() { return forecastFinish; }
    public Long getBudgetMinor() { return budgetMinor; }
    public Long getCommittedMinor() { return committedMinor; }
    public Long getCertifiedMinor() { return certifiedMinor; }
    public Integer getOpenRisks() { return openRisks; }
    public Integer getHighRisks() { return highRisks; }
    public Integer getCriticalRisks() { return criticalRisks; }
    public Integer getSlipDays() { return slipDays; }
    public Integer getOpenIssues() { return openIssues; }
    public UUID getSubmittedBy() { return submittedBy; }
    public Instant getSubmittedAt() { return submittedAt; }
    public UUID getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewComment() { return reviewComment; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
