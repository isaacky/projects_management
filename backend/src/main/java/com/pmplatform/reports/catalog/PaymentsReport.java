package com.pmplatform.reports.catalog;

import static com.pmplatform.reports.Rows.col;
import static com.pmplatform.reports.Rows.row;

import com.pmplatform.contracts.ContractService;
import com.pmplatform.contracts.ContractStatus;
import com.pmplatform.contracts.InvoiceStatus;
import com.pmplatform.contracts.api.ContractDtos.ContractDetail;
import com.pmplatform.contracts.api.ContractDtos.ContractSummary;
import com.pmplatform.contracts.api.ContractDtos.InvoiceView;
import com.pmplatform.portfolio.ProjectService;
import com.pmplatform.reports.Report;
import com.pmplatform.reports.ReportModel.ColumnType;
import com.pmplatform.reports.ReportModel.Filter;
import com.pmplatform.reports.ReportModel.FilterType;
import com.pmplatform.reports.ReportModel.ReportResult;
import com.pmplatform.reports.Rows;
import com.pmplatform.platform.web.BusinessRuleException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Invoices and payment certificates over a period: certified, approved and paid. */
@Component
public class PaymentsReport implements Report {

    private static final ZoneId EAT = ZoneId.of("Africa/Nairobi");

    private final ContractService contracts;
    private final ProjectService projects;
    private final Clock clock;

    public PaymentsReport(ContractService contracts, ProjectService projects, Clock clock) {
        this.contracts = contracts;
        this.projects = projects;
        this.clock = clock;
    }

    @Override public String id() { return "payments"; }
    @Override public String title() { return "Invoices and payments"; }
    @Override public String category() { return "Contracts"; }
    @Override public String description() {
        return "Invoices and payment certificates received in a period: gross, retention and net amounts, where "
                + "each is in certification, approval and payment, and the payment reference.";
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(EAT));
    }

    @Override
    public List<Filter> filters(UUID tenantId, Map<String, String> params) {
        LocalDate t = today();
        return List.of(
                new Filter("from", "Invoice date from", FilterType.DATE, List.of(), t.withDayOfYear(1).toString()),
                new Filter("to", "to", FilterType.DATE, List.of(), t.toString()),
                Common.projectFilter(projects, tenantId),
                new Filter("status", "Status", FilterType.SELECT,
                        Common.withAll("Any status", Rows.options(InvoiceStatus.values())), ""));
    }

    @Override
    public ReportResult run(UUID tenantId, Map<String, String> params) {
        LocalDate t = today();
        LocalDate from = params.containsKey("from") ? Rows.date(params, "from") : t.withDayOfYear(1);
        LocalDate to = params.containsKey("to") ? Rows.date(params, "to") : t;
        if (from != null && to != null && to.isBefore(from)) {
            throw new BusinessRuleException("The end date is before the start date");
        }
        UUID projectId = Rows.uuid(params, "projectId");
        InvoiceStatus status = Rows.choice(params, "status", InvoiceStatus.class);
        List<Object[]> found = new ArrayList<>();
        for (ContractSummary s : contracts.list(tenantId, projectId, null)) {
            if (s.status() == ContractStatus.DRAFT || s.status() == ContractStatus.CANCELLED) {
                continue;
            }
            ContractDetail c = contracts.get(tenantId, s.id());
            for (InvoiceView i : c.invoices()) {
                if ((from != null && i.invoiceDate().isBefore(from)) || (to != null && i.invoiceDate().isAfter(to))
                        || (status != null && i.status() != status)) {
                    continue;
                }
                found.add(new Object[] {c, i});
            }
        }
        found.sort(Comparator.comparing((Object[] o) -> ((InvoiceView) o[1]).invoiceDate())
                .thenComparing(o -> ((ContractDetail) o[0]).number()));
        List<Map<String, Object>> rows = new ArrayList<>();
        long paid = 0;
        long awaiting = 0;
        int rejected = 0;
        int otherCurrency = 0;
        for (Object[] o : found) {
            ContractDetail c = (ContractDetail) o[0];
            InvoiceView i = (InvoiceView) o[1];
            boolean kes = "KES".equals(c.currency());
            otherCurrency += kes ? 0 : 1;
            rejected += i.status() == InvoiceStatus.REJECTED ? 1 : 0;
            if (kes && i.status() == InvoiceStatus.PAID) {
                paid += i.netMinor();
            } else if (kes && (i.status() == InvoiceStatus.APPROVED || i.status() == InvoiceStatus.CERTIFIED)) {
                awaiting += i.netMinor();
            }
            Map<String, Object> r = row("contract", c.number(), "supplier", c.supplier() == null ? null : c.supplier().name(),
                    "project", c.projectCode(), "currency", c.currency(), "kind", Rows.text(i.kind()),
                    "certificate", i.certificateNo() > 0 ? i.certificateNo() : null, "supplierRef", i.supplierRef(),
                    "invoiceDate", i.invoiceDate(), "milestone", i.milestoneTitle(), "gross", i.grossMinor(),
                    "retention", i.retentionMinor(), "net", i.netMinor(), "status", Rows.text(i.status()),
                    "certifiedOn", i.certifiedAt() == null ? null : LocalDate.ofInstant(i.certifiedAt(), EAT),
                    "approvedOn", i.approvedAt() == null ? null : LocalDate.ofInstant(i.approvedAt(), EAT),
                    "paidOn", i.paidOn(), "paymentRef", i.paymentRef());
            r.put("_link", "/contracts/" + c.id());
            r.put("_currency", c.currency());
            r.put("_rejected", i.status() == InvoiceStatus.REJECTED);
            rows.add(r);
        }
        List<String> notes = new ArrayList<>();
        notes.add("Of these invoices: paid " + ProcurementPlanReport.money(paid, "KES")
                + "; certified or approved and awaiting payment " + ProcurementPlanReport.money(awaiting, "KES") + ".");
        if (rejected > 0) {
            notes.add(rejected + " rejected invoice" + (rejected == 1 ? " is" : "s are") + " listed but left out of the "
                    + "totals.");
        }
        if (otherCurrency > 0) {
            notes.add(otherCurrency + " invoice" + (otherCurrency == 1 ? " is" : "s are") + " in another currency: "
                    + "listed, but not in the KES totals.");
        }
        String subtitle = "Invoices dated " + (from == null ? "from the start" : from) + " to " + (to == null ? "today" : to)
                + (status == null ? "" : " · " + Rows.text(status));
        return new ReportResult(id(), title(), subtitle, "KES",
                List.of(col("contract", "Contract", ColumnType.TEXT), col("supplier", "Supplier", ColumnType.TEXT),
                        col("project", "Project", ColumnType.TEXT), col("currency", "Currency", ColumnType.TEXT),
                        col("kind", "Type", ColumnType.TEXT),
                        col("certificate", "Certificate", ColumnType.INTEGER),
                        col("supplierRef", "Supplier's ref", ColumnType.TEXT),
                        col("invoiceDate", "Invoice date", ColumnType.DATE),
                        col("milestone", "Milestone", ColumnType.TEXT), col("gross", "Gross", ColumnType.MONEY),
                        col("retention", "Retention", ColumnType.MONEY), col("net", "Net", ColumnType.MONEY),
                        col("status", "Status", ColumnType.TEXT), col("certifiedOn", "Certified", ColumnType.DATE),
                        col("approvedOn", "Approved", ColumnType.DATE), col("paidOn", "Paid", ColumnType.DATE),
                        col("paymentRef", "Payment ref", ColumnType.TEXT)),
                rows, Rows.totals(rows.stream().filter(r -> "KES".equals(r.get("_currency"))
                        && !Boolean.TRUE.equals(r.get("_rejected"))).toList(), "gross", "retention", "net"),
                notes, Instant.now(clock));
    }
}
