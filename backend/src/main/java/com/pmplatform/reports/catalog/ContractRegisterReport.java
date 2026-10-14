package com.pmplatform.reports.catalog;

import static com.pmplatform.reports.Rows.col;
import static com.pmplatform.reports.Rows.row;

import com.pmplatform.contracts.ContractService;
import com.pmplatform.contracts.ContractStatus;
import com.pmplatform.contracts.api.ContractDtos.ContractSummary;
import com.pmplatform.portfolio.ProjectService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Every contract with its value, variations, payments and retention. */
@Component
public class ContractRegisterReport implements Report {

    private final ContractService contracts;
    private final ProjectService projects;
    private final Clock clock;

    public ContractRegisterReport(ContractService contracts, ProjectService projects, Clock clock) {
        this.contracts = contracts;
        this.projects = projects;
        this.clock = clock;
    }

    @Override public String id() { return "contract-register"; }
    @Override public String title() { return "Contract register"; }
    @Override public String category() { return "Contracts"; }
    @Override public String description() {
        return "Contracts with supplier, project, original and current value, variations, amounts certified and "
                + "paid, retention held, end date and defects liability period.";
    }

    @Override
    public List<Filter> filters(UUID tenantId, Map<String, String> params) {
        return List.of(Common.projectFilter(projects, tenantId),
                new Filter("status", "Status", FilterType.SELECT,
                        Common.withAll("All but cancelled", Rows.options(ContractStatus.values())), ""));
    }

    @Override
    public ReportResult run(UUID tenantId, Map<String, String> params) {
        UUID projectId = Rows.uuid(params, "projectId");
        ContractStatus status = Rows.choice(params, "status", ContractStatus.class);
        List<Map<String, Object>> rows = new ArrayList<>();
        List<ContractSummary> list = new ArrayList<>(contracts.list(tenantId, projectId, null));
        list.sort(Comparator.comparing(ContractSummary::number));
        for (ContractSummary c : list) {
            if (status == null ? c.status() == ContractStatus.CANCELLED : c.status() != status) {
                continue;
            }
            Map<String, Object> r = row("number", c.number(), "title", c.title(),
                    "supplier", c.supplier() == null ? null : c.supplier().name(), "project", c.projectCode(),
                    "status", Rows.text(c.status()), "currency", c.currency(), "original", c.originalValueMinor(),
                    "current", c.currentValueMinor(),
                    "variationPct", Rows.pct(c.currentValueMinor() - c.originalValueMinor(), c.originalValueMinor()),
                    "certified", c.certifiedMinor(), "paid", c.paidMinor(),
                    "certifiedPct", Rows.pct(c.certifiedMinor(), c.currentValueMinor()),
                    "retention", c.retentionHeldMinor(), "endDate", c.endDate(), "dlpEnds", c.defectsLiabilityEnds(),
                    "manager", c.contractManager() == null ? null : c.contractManager().name());
            r.put("_link", "/contracts/" + c.id());
            r.put("_currency", c.currency());
            rows.add(r);
        }
        String subtitle = (status == null ? "All contracts except cancelled" : Rows.text(status)
                + " contracts") + (projectId == null ? "" : " · " + list.stream().findFirst()
                        .map(ContractSummary::projectCode).orElse(""));
        return new ReportResult(id(), title(), subtitle, "KES",
                List.of(col("number", "Contract", ColumnType.TEXT), col("title", "Title", ColumnType.TEXT),
                        col("supplier", "Supplier", ColumnType.TEXT), col("project", "Project", ColumnType.TEXT),
                        col("status", "Status", ColumnType.TEXT), col("currency", "Currency", ColumnType.TEXT),
                        col("original", "Original value", ColumnType.MONEY),
                        col("current", "Current value", ColumnType.MONEY),
                        col("variationPct", "Variation %", ColumnType.PERCENT),
                        col("certified", "Certified", ColumnType.MONEY), col("paid", "Paid", ColumnType.MONEY),
                        col("certifiedPct", "Certified %", ColumnType.PERCENT),
                        col("retention", "Retention held", ColumnType.MONEY), col("endDate", "Ends", ColumnType.DATE),
                        col("dlpEnds", "Defects period ends", ColumnType.DATE),
                        col("manager", "Contract manager", ColumnType.TEXT)),
                rows, Rows.totals(inKes(rows), "original", "current", "certified", "paid", "retention"),
                notes(rows), Instant.now(clock));
    }

    static List<Map<String, Object>> inKes(List<Map<String, Object>> rows) {
        return rows.stream().filter(r -> "KES".equals(r.get("_currency"))).toList();
    }

    private static List<String> notes(List<Map<String, Object>> rows) {
        List<String> notes = new ArrayList<>();
        notes.add("Variation % compares the current value with the value at signing (PPADA s.139 caps increases at 25%).");
        long other = rows.size() - inKes(rows).size();
        if (other > 0) {
            notes.add(other + " contract" + (other == 1 ? " is" : "s are") + " in another currency: listed, but not in "
                    + "the KES totals.");
        }
        return notes;
    }
}
