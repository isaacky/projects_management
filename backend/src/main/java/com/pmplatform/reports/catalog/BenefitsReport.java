package com.pmplatform.reports.catalog;

import static com.pmplatform.reports.Rows.col;
import static com.pmplatform.reports.Rows.row;

import com.pmplatform.benefits.BenefitRating;
import com.pmplatform.benefits.BenefitService;
import com.pmplatform.benefits.BenefitStatus;
import com.pmplatform.benefits.api.BenefitDtos.BenefitView;
import com.pmplatform.benefits.api.BenefitDtos.PortfolioView;
import com.pmplatform.benefits.api.BenefitDtos.ReviewView;
import com.pmplatform.reports.Report;
import com.pmplatform.reports.ReportModel.ColumnType;
import com.pmplatform.reports.ReportModel.Filter;
import com.pmplatform.reports.ReportModel.FilterType;
import com.pmplatform.reports.ReportModel.Option;
import com.pmplatform.reports.ReportModel.ReportResult;
import com.pmplatform.reports.Rows;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Benefits across the portfolio against their targets, with money realised. */
@Component
public class BenefitsReport implements Report {

    private final BenefitService benefits;
    private final Clock clock;

    public BenefitsReport(BenefitService benefits, Clock clock) {
        this.benefits = benefits;
        this.clock = clock;
    }

    @Override public String id() { return "benefits"; }
    @Override public String title() { return "Benefits realisation"; }
    @Override public String category() { return "Benefits"; }
    @Override public String description() {
        return "Each benefit's baseline, target and latest reading, progress against where it should be by now, "
                + "its rating, the next reading due, and money realised against value at target.";
    }

    @Override
    public List<Filter> filters(UUID tenantId, Map<String, String> params) {
        List<Option> ratings = new ArrayList<>(Common.withAll("Any rating", Rows.options(BenefitRating.values())));
        ratings.add(new Option("NONE", "Not measured yet"));
        return List.of(
                new Filter("status", "Status", FilterType.SELECT,
                        Common.withAll("Any status", Rows.options(BenefitStatus.values())), "ACTIVE"),
                new Filter("rating", "Rating", FilterType.SELECT, ratings, ""));
    }

    @Override
    public ReportResult run(UUID tenantId, Map<String, String> params) {
        BenefitStatus status = Rows.choice(params, "status", BenefitStatus.class, BenefitStatus.ACTIVE);
        boolean unmeasured = "NONE".equals(Rows.str(params, "rating"));
        BenefitRating rating = unmeasured ? null : Rows.choice(params, "rating", BenefitRating.class);
        PortfolioView v = benefits.portfolio(tenantId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BenefitView b : v.benefits()) {
            if ((status != null && b.status() != status) || (unmeasured && b.rating() != null)
                    || (rating != null && b.rating() != rating)) {
                continue;
            }
            Map<String, Object> r = row("project", b.project().code(), "ref", "B" + b.number(), "title", b.title(),
                    "category", Rows.text(b.category()), "measure", b.measure(), "unit", b.unit(),
                    "baseline", b.baselineValue(), "target", b.targetValue(), "targetDate", b.targetDate(),
                    "latest", b.latestValue(), "latestOn", b.latestOn(), "progress", b.progressPct(),
                    "expected", b.status() == BenefitStatus.ACTIVE ? b.expectedPct() : null,
                    "rating", b.status() == BenefitStatus.ACTIVE
                            ? (b.rating() == null ? "Not measured yet" : Rows.text(b.rating()))
                            : Rows.text(b.status()),
                    "nextDue", b.nextDue(), "overdue", b.overdue() ? "Overdue" : null,
                    "owner", b.owner() == null ? null : b.owner().name(), "value", b.annualValueMinor(),
                    "realised", b.annualValueMinor() == null ? null : b.realisedMinor());
            r.put("_link", "/projects/" + b.project().id() + "?tab=benefits");
            rows.add(r);
        }
        List<String> notes = new ArrayList<>();
        notes.add("Progress = how far the latest reading has come from the baseline towards the target. Expected = "
                + "where it should be by now on a straight line between the two dates. On track ≥ 90% of expected, "
                + "at risk ≥ 60%, otherwise off track.");
        List<ReviewView> due = v.upcomingReviews();
        if (!due.isEmpty()) {
            ReviewView next = due.getFirst();
            notes.add(due.size() + " post-implementation review" + (due.size() == 1 ? "" : "s") + " planned; next: "
                    + next.project().code() + " " + next.title() + " on " + next.dueDate() + ".");
        }
        String subtitle = (status == null ? "All benefits" : Rows.text(status) + " benefits")
                + (unmeasured ? " · not measured yet" : rating == null ? "" : " · " + Rows.text(rating).toLowerCase());
        return new ReportResult(id(), title(), subtitle, "KES",
                List.of(col("project", "Project", ColumnType.TEXT), col("ref", "Benefit", ColumnType.TEXT),
                        col("title", "Title", ColumnType.TEXT), col("category", "Category", ColumnType.TEXT),
                        col("measure", "Measure", ColumnType.TEXT), col("unit", "Unit", ColumnType.TEXT),
                        col("baseline", "Baseline", ColumnType.NUMBER), col("target", "Target", ColumnType.NUMBER),
                        col("targetDate", "Target date", ColumnType.DATE), col("latest", "Latest", ColumnType.NUMBER),
                        col("latestOn", "Measured", ColumnType.DATE), col("progress", "Progress %", ColumnType.PERCENT),
                        col("expected", "Expected %", ColumnType.PERCENT), col("rating", "Rating", ColumnType.TEXT),
                        col("nextDue", "Next reading", ColumnType.DATE), col("overdue", "Overdue", ColumnType.TEXT),
                        col("owner", "Owner", ColumnType.TEXT), col("value", "Value a year at target", ColumnType.MONEY),
                        col("realised", "Realised a year", ColumnType.MONEY)),
                rows, Rows.totals(rows, "value", "realised"), notes, Instant.now(clock));
    }
}
