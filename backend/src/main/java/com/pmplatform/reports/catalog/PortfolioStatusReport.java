package com.pmplatform.reports.catalog;

import static com.pmplatform.reports.Rows.col;
import static com.pmplatform.reports.Rows.row;

import com.pmplatform.execution.ExecutionService;
import com.pmplatform.execution.api.ExecutionDtos.ProjectHealth;
import com.pmplatform.portfolio.ProjectPhase;
import com.pmplatform.reports.Report;
import com.pmplatform.reports.ReportModel.ColumnType;
import com.pmplatform.reports.ReportModel.Filter;
import com.pmplatform.reports.ReportModel.FilterType;
import com.pmplatform.reports.ReportModel.ReportResult;
import com.pmplatform.reports.Rows;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Every project's schedule, cost and risk position, with its latest reported rating. */
@Component
public class PortfolioStatusReport implements Report {

    private final ExecutionService execution;
    private final Clock clock;

    public PortfolioStatusReport(ExecutionService execution, Clock clock) {
        this.execution = execution;
        this.clock = clock;
    }

    @Override public String id() { return "portfolio-status"; }
    @Override public String title() { return "Portfolio status"; }
    @Override public String category() { return "Portfolio"; }
    @Override public String description() {
        return "Each project's progress against plan, SPI and CPI, forecast finish, money committed and certified, "
                + "open risks and issues, and its latest status rating.";
    }

    @Override
    public List<Filter> filters(UUID tenantId, Map<String, String> params) {
        return List.of(new Filter("phase", "Phase", FilterType.SELECT,
                Common.withAll("All phases", Rows.options(ProjectPhase.values())), ""));
    }

    @Override
    public ReportResult run(UUID tenantId, Map<String, String> params) {
        ProjectPhase phase = Rows.choice(params, "phase", ProjectPhase.class);
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean otherCurrency = false;
        for (ProjectHealth h : execution.portfolioHealth(tenantId)) {
            if (phase != null && h.phase() != phase) {
                continue;
            }
            otherCurrency |= !"KES".equals(h.currency());
            Map<String, Object> r = row("code", h.code(), "name", h.name(), "phase", Rows.text(h.phase()),
                    "budget", h.budgetMinor(), "committed", h.committedMinor(), "certified", h.certifiedMinor(),
                    "plannedPct", h.plannedPct(), "earnedPct", h.earnedPct(), "spi", h.spi(), "cpi", h.cpi(),
                    "forecastFinish", h.forecastFinish(), "slipDays", h.slipDays(), "openRisks", h.openRisks(),
                    "highRisks", h.highRisks(), "openIssues", h.openIssues(),
                    "reported", h.lastOverall() == null ? null : Rows.text(h.lastOverall())
                            + (h.lastReportPeriod() == null ? "" : " (" + h.lastReportPeriod() + ")"),
                    "suggested", Rows.text(h.suggestedOverall()));
            r.put("_link", "/projects/" + h.projectId());
            r.put("_currency", h.currency());
            rows.add(r);
        }
        List<String> notes = new ArrayList<>();
        notes.add("SPI = earned ÷ planned progress; CPI = earned value ÷ amounts certified (a guide until actual costs "
                + "come from the ERP).");
        if (otherCurrency) {
            notes.add("Some projects are not in KES; their amounts are in their own currency and the totals mix them.");
        }
        return new ReportResult(id(), title(), phase == null ? "All phases" : "Phase: " + Rows.text(phase),
                "KES",
                List.of(col("code", "Project", ColumnType.TEXT), col("name", "Name", ColumnType.TEXT),
                        col("phase", "Phase", ColumnType.TEXT), col("budget", "Budget", ColumnType.MONEY),
                        col("committed", "Committed", ColumnType.MONEY), col("certified", "Certified", ColumnType.MONEY),
                        col("plannedPct", "Planned %", ColumnType.PERCENT), col("earnedPct", "Earned %", ColumnType.PERCENT),
                        col("spi", "SPI", ColumnType.NUMBER), col("cpi", "CPI", ColumnType.NUMBER),
                        col("forecastFinish", "Forecast finish", ColumnType.DATE),
                        col("slipDays", "Slip (days)", ColumnType.INTEGER),
                        col("openRisks", "Open risks", ColumnType.INTEGER),
                        col("highRisks", "High risks", ColumnType.INTEGER),
                        col("openIssues", "Open issues", ColumnType.INTEGER),
                        col("reported", "Last reported", ColumnType.TEXT),
                        col("suggested", "From figures", ColumnType.TEXT)),
                rows, Rows.totals(rows, "budget", "committed", "certified", "openRisks", "highRisks", "openIssues"),
                notes, Instant.now(clock));
    }
}
