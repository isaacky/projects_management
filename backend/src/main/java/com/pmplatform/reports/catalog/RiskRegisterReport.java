package com.pmplatform.reports.catalog;

import static com.pmplatform.reports.Rows.col;
import static com.pmplatform.reports.Rows.row;

import com.pmplatform.execution.ExecutionRules.RiskLevel;
import com.pmplatform.execution.ExecutionService;
import com.pmplatform.execution.RiskStatus;
import com.pmplatform.execution.api.ExecutionDtos.RiskView;
import com.pmplatform.portfolio.Project;
import com.pmplatform.portfolio.ProjectService;
import com.pmplatform.portfolio.ProjectStatus;
import com.pmplatform.reports.Report;
import com.pmplatform.reports.ReportModel.ColumnType;
import com.pmplatform.reports.ReportModel.Filter;
import com.pmplatform.reports.ReportModel.FilterType;
import com.pmplatform.reports.ReportModel.ReportResult;
import com.pmplatform.reports.Rows;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Risks across the portfolio, highest score first. */
@Component
public class RiskRegisterReport implements Report {

    private final ExecutionService execution;
    private final ProjectService projects;
    private final Clock clock;

    public RiskRegisterReport(ExecutionService execution, ProjectService projects, Clock clock) {
        this.execution = execution;
        this.projects = projects;
        this.clock = clock;
    }

    @Override public String id() { return "risk-register"; }
    @Override public String title() { return "Risk register"; }
    @Override public String category() { return "Portfolio"; }
    @Override public String description() {
        return "Risks across all projects with probability, impact and score, the response, owner and due date, "
                + "highest score first.";
    }

    @Override
    public List<Filter> filters(UUID tenantId, Map<String, String> params) {
        return List.of(Common.projectFilter(projects, tenantId),
                new Filter("level", "Level", FilterType.SELECT, Common.withAll("All levels", Rows.options(RiskLevel.values())), ""),
                new Filter("status", "Status", FilterType.SELECT, Common.withAll("Open and closed",
                        Rows.options(RiskStatus.values())), "OPEN"));
    }

    @Override
    public ReportResult run(UUID tenantId, Map<String, String> params) {
        UUID projectId = Rows.uuid(params, "projectId");
        RiskLevel level = Rows.choice(params, "level", RiskLevel.class);
        RiskStatus status = Rows.choice(params, "status", RiskStatus.class, RiskStatus.OPEN);
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<RiskLevel, Integer> byLevel = new EnumMap<>(RiskLevel.class);
        List<Project> scope = projects.list(tenantId, null).stream()
                .filter(p -> projectId == null ? p.getStatus() != ProjectStatus.CANCELLED : p.getId().equals(projectId))
                .toList();
        List<Object[]> sorted = new ArrayList<>();
        for (Project p : scope) {
            for (RiskView r : execution.risks(tenantId, p.getId())) {
                if ((level != null && r.level() != level) || (status != null && r.status() != status)) {
                    continue;
                }
                sorted.add(new Object[] {p, r});
            }
        }
        sorted.sort(Comparator.comparingInt((Object[] o) -> ((RiskView) o[1]).score()).reversed()
                .thenComparing(o -> ((Project) o[0]).getCode()));
        for (Object[] o : sorted) {
            Project p = (Project) o[0];
            RiskView r = (RiskView) o[1];
            byLevel.merge(r.level(), 1, Integer::sum);
            Map<String, Object> m = row("project", p.getCode(), "ref", "R" + r.number(), "title", r.title(),
                    "category", Rows.text(r.category()), "probability", r.probability(), "impact", r.impact(),
                    "score", r.score(), "level", Rows.text(r.level()), "response", Rows.text(r.response()),
                    "mitigation", r.mitigation(), "owner", r.owner() == null ? null : r.owner().name(),
                    "due", r.dueDate(), "overdue", r.overdue() ? "Overdue" : null, "status", Rows.text(r.status()));
            m.put("_link", "/projects/" + p.getId() + "?tab=risks");
            rows.add(m);
        }
        List<String> notes = new ArrayList<>();
        if (!byLevel.isEmpty()) {
            StringBuilder s = new StringBuilder("By level: ");
            byLevel.forEach((k, v) -> s.append(Rows.text(k)).append(' ').append(v).append(", "));
            notes.add(s.substring(0, s.length() - 2) + ".");
        }
        notes.add("Score = probability × impact on a 5×5 matrix: 1–4 low, 5–9 medium, 10–16 high, 20–25 critical.");
        String subtitle = (status == null ? "Open and closed risks" : Rows.text(status) + " risks")
                + (level == null ? "" : " · " + Rows.text(level))
                + (projectId == null ? "" : " · " + scope.stream().findFirst().map(Project::getCode).orElse(""));
        return new ReportResult(id(), title(), subtitle, "KES",
                List.of(col("project", "Project", ColumnType.TEXT), col("ref", "Risk", ColumnType.TEXT),
                        col("title", "Title", ColumnType.TEXT), col("category", "Category", ColumnType.TEXT),
                        col("probability", "P", ColumnType.INTEGER), col("impact", "I", ColumnType.INTEGER),
                        col("score", "Score", ColumnType.INTEGER), col("level", "Level", ColumnType.TEXT),
                        col("response", "Response", ColumnType.TEXT), col("mitigation", "Mitigation", ColumnType.TEXT),
                        col("owner", "Owner", ColumnType.TEXT), col("due", "Due", ColumnType.DATE),
                        col("overdue", "Overdue", ColumnType.TEXT), col("status", "Status", ColumnType.TEXT)),
                rows, Map.of(), notes, Instant.now(clock));
    }
}
