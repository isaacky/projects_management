package com.pmplatform.reports.catalog;

import static com.pmplatform.reports.Rows.col;
import static com.pmplatform.reports.Rows.row;

import com.pmplatform.contracts.ContractService;
import com.pmplatform.contracts.api.ContractDtos.SupplierPerformance;
import com.pmplatform.procurement.supplier.Supplier;
import com.pmplatform.procurement.supplier.SupplierService;
import com.pmplatform.reports.Report;
import com.pmplatform.reports.ReportModel.ColumnType;
import com.pmplatform.reports.ReportModel.Filter;
import com.pmplatform.reports.ReportModel.ReportResult;
import com.pmplatform.reports.Rows;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Suppliers' contracts and completion ratings. */
@Component
public class SupplierPerformanceReport implements Report {

    private final ContractService contracts;
    private final SupplierService suppliers;
    private final Clock clock;

    public SupplierPerformanceReport(ContractService contracts, SupplierService suppliers, Clock clock) {
        this.contracts = contracts;
        this.suppliers = suppliers;
        this.clock = clock;
    }

    @Override public String id() { return "supplier-performance"; }
    @Override public String title() { return "Supplier performance"; }
    @Override public String category() { return "Contracts"; }
    @Override public String description() {
        return "Each supplier's contracts, how many are completed, the average completion rating (quality, "
                + "timeliness and compliance, 1–5) and the total contract value.";
    }

    @Override
    public List<Filter> filters(UUID tenantId, Map<String, String> params) {
        return List.of();
    }

    @Override
    public ReportResult run(UUID tenantId, Map<String, String> params) {
        List<SupplierPerformance> perf = new ArrayList<>(contracts.supplierPerformance(tenantId));
        perf.sort(Comparator.comparingLong(SupplierPerformance::totalValueMinor).reversed());
        Map<UUID, Supplier> names = suppliers.byIds(tenantId, perf.stream().map(SupplierPerformance::supplierId).toList());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SupplierPerformance p : perf) {
            Supplier s = names.get(p.supplierId());
            rows.add(row("supplier", s == null ? "Unknown supplier" : s.getName(), "kraPin", s == null ? null : s.getKraPin(),
                    "agpo", s == null ? null : Rows.text(s.getAgpoCategory()), "contracts", p.contracts(),
                    "completed", p.completed(),
                    "rating", p.averageRating() == null ? null
                            : BigDecimal.valueOf(p.averageRating()).setScale(1, RoundingMode.HALF_UP),
                    "value", p.totalValueMinor()));
        }
        return new ReportResult(id(), title(), "All suppliers with contracts", "KES",
                List.of(col("supplier", "Supplier", ColumnType.TEXT), col("kraPin", "KRA PIN", ColumnType.TEXT),
                        col("agpo", "AGPO group", ColumnType.TEXT), col("contracts", "Contracts", ColumnType.INTEGER),
                        col("completed", "Completed", ColumnType.INTEGER),
                        col("rating", "Average rating", ColumnType.NUMBER),
                        col("value", "Contract value", ColumnType.MONEY)),
                rows, Rows.totals(rows, "contracts", "completed", "value"),
                List.of("The rating is the average of the quality, timeliness and compliance scores given when each "
                        + "contract was completed.", "Contract value adds up each supplier's contracts as they stand, "
                        + "whatever their currency; the contract register shows each contract's currency."),
                Instant.now(clock));
    }
}
