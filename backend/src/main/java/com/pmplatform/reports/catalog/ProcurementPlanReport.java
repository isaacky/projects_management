package com.pmplatform.reports.catalog;

import static com.pmplatform.reports.Rows.col;
import static com.pmplatform.reports.Rows.row;

import com.pmplatform.procurement.plan.LineStage;
import com.pmplatform.procurement.plan.ProcurementPlanService;
import com.pmplatform.procurement.plan.api.PlanDtos.LineView;
import com.pmplatform.procurement.plan.api.PlanDtos.PlanSummary;
import com.pmplatform.procurement.plan.api.PlanDtos.PlanView;
import com.pmplatform.reports.Report;
import com.pmplatform.reports.ReportModel.ColumnType;
import com.pmplatform.reports.ReportModel.Filter;
import com.pmplatform.reports.ReportModel.FilterType;
import com.pmplatform.reports.ReportModel.Option;
import com.pmplatform.reports.ReportModel.ReportResult;
import com.pmplatform.reports.Rows;
import com.pmplatform.platform.web.NotFoundException;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** The annual procurement plan line by line, against what has been requisitioned and awarded. */
@Component
public class ProcurementPlanReport implements Report {

    private final ProcurementPlanService plans;
    private final Clock clock;

    public ProcurementPlanReport(ProcurementPlanService plans, Clock clock) {
        this.plans = plans;
        this.clock = clock;
    }

    @Override public String id() { return "procurement-plan"; }
    @Override public String title() { return "Procurement plan against actual"; }
    @Override public String category() { return "Procurement"; }
    @Override public String description() {
        return "Each line of a year's procurement plan with its estimate, approved amount, what requisitions draw "
                + "on it, what has been awarded and how far it has got; unplanned requisitions and the AGPO share.";
    }

    /** The chosen plan, or the one covering today, or the latest. */
    private UUID planId(UUID tenantId, Map<String, String> params) {
        UUID chosen = Rows.uuid(params, "planId");
        if (chosen != null) {
            return chosen;
        }
        List<PlanSummary> all = plans.list(tenantId);
        return all.stream().filter(PlanSummary::current).findFirst().or(() -> all.stream().findFirst())
                .map(PlanSummary::id).orElse(null);
    }

    @Override
    public List<Filter> filters(UUID tenantId, Map<String, String> params) {
        List<Option> planOptions = plans.list(tenantId).stream()
                .map(p -> new Option(p.id().toString(), p.financialYear() + (p.current() ? " (current)" : ""))).toList();
        UUID planId = planId(tenantId, params);
        List<Option> depts = new ArrayList<>();
        depts.add(new Option("", "All departments"));
        if (planId != null) {
            plans.get(tenantId, planId).departments().forEach(d -> depts.add(new Option(d.id().toString(), d.name())));
        }
        return List.of(
                new Filter("planId", "Financial year", FilterType.SELECT, planOptions,
                        planId == null ? "" : planId.toString()),
                new Filter("departmentId", "Department", FilterType.SELECT, depts, ""),
                new Filter("quarter", "Quarter", FilterType.SELECT, List.of(new Option("", "All quarters"),
                        new Option("1", "Q1"), new Option("2", "Q2"), new Option("3", "Q3"), new Option("4", "Q4")), ""),
                new Filter("stage", "Progress", FilterType.SELECT,
                        Common.withAll("Any progress", Rows.options(LineStage.values())), ""));
    }

    @Override
    public ReportResult run(UUID tenantId, Map<String, String> params) {
        UUID planId = planId(tenantId, params);
        if (planId == null) {
            throw new NotFoundException("There is no procurement plan yet");
        }
        PlanView plan = plans.get(tenantId, planId);
        UUID dept = Rows.uuid(params, "departmentId");
        String quarter = Rows.str(params, "quarter");
        LineStage stage = Rows.choice(params, "stage", LineStage.class);
        List<Map<String, Object>> rows = new ArrayList<>();
        int behind = 0;
        for (LineView l : plan.lines()) {
            if (l.cancelled() || (dept != null && !dept.equals(l.departmentId()))
                    || (quarter != null && !quarter.equals(String.valueOf(l.quarter())))
                    || (stage != null && l.stage() != stage)) {
                continue;
            }
            behind += l.behind() ? 1 : 0;
            Map<String, Object> r = row("ref", l.reference(), "department", l.department(),
                    "description", l.description(), "category", Rows.text(l.category()), "method", l.methodCode(),
                    "quarter", "Q" + l.quarter(), "reserved", Rows.text(l.reservedGroup()),
                    "estimate", l.estimateMinor(), "approved", l.approvedEstimateMinor(),
                    "requisitioned", l.committedMinor(), "awarded", l.awardedMinor(),
                    "stage", Rows.text(l.stage()), "behind", l.behind() ? "Behind" : null);
            r.put("_link", "/procurement-plans/" + plan.id());
            rows.add(r);
        }
        var s = plan.summary();
        List<String> notes = new ArrayList<>();
        notes.add("Reserved for AGPO groups: " + s.agpoPct() + "% of the plan (target "
                + s.agpoTargetPct().stripTrailingZeros().toPlainString() + "%).");
        if (behind > 0) {
            notes.add(behind + " line" + (behind == 1 ? " is" : "s are") + " behind: the planned quarter has ended "
                    + "with no requisition.");
        }
        notes.add("Unplanned requisitions this year: " + s.unplannedCount() + " (" + money(s.unplannedMinor(), plan.currency())
                + ").");
        String subtitle = "Financial year " + plan.financialYear() + " · " + Rows.text(plan.status())
                + (plan.revision() > 0 ? " (approved " + plan.revision() + " time" + (plan.revision() == 1 ? "" : "s") + ")" : "")
                + (dept == null ? "" : " · " + plan.departments().stream().filter(d -> d.id().equals(dept))
                        .map(d -> d.name()).findFirst().orElse(""))
                + (quarter == null ? "" : " · Q" + quarter);
        return new ReportResult(id(), title(), subtitle, plan.currency(),
                List.of(col("ref", "Reference", ColumnType.TEXT), col("department", "Department", ColumnType.TEXT),
                        col("description", "Description", ColumnType.TEXT), col("category", "Category", ColumnType.TEXT),
                        col("method", "Method", ColumnType.TEXT), col("quarter", "Quarter", ColumnType.TEXT),
                        col("reserved", "Reserved for", ColumnType.TEXT), col("estimate", "Estimate", ColumnType.MONEY),
                        col("approved", "Approved", ColumnType.MONEY),
                        col("requisitioned", "Requisitioned", ColumnType.MONEY),
                        col("awarded", "Awarded", ColumnType.MONEY), col("stage", "Progress", ColumnType.TEXT),
                        col("behind", "Behind", ColumnType.TEXT)),
                rows, Rows.totals(rows, "estimate", "approved", "requisitioned", "awarded"), notes, Instant.now(clock));
    }

    static String money(long minor, String currency) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.ENGLISH);
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return currency + " " + f.format(minor / 100.0);
    }
}
