package com.pmplatform.reports;

import com.pmplatform.platform.audit.AuditService;
import com.pmplatform.platform.json.JsonText;
import com.pmplatform.platform.user.CurrentUserService;
import com.pmplatform.platform.web.NotFoundException;
import com.pmplatform.reports.ReportModel.Filter;
import com.pmplatform.reports.ReportModel.ReportInfo;
import com.pmplatform.reports.ReportModel.ReportResult;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The report catalogue: describes, runs and exports the {@link Report} beans. */
@Service
public class ReportService {

    /** Catalogue order of the groups. */
    private static final List<String> CATEGORIES = List.of("Portfolio", "Procurement", "Contracts", "Benefits");

    private final Map<String, Report> reports;
    private final CurrentUserService currentUser;
    private final AuditService audit;

    public ReportService(List<Report> reports, CurrentUserService currentUser, AuditService audit) {
        this.reports = reports.stream().collect(Collectors.toMap(Report::id, Function.identity()));
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ReportInfo> catalogue(UUID tenantId) {
        currentUser.require();
        return reports.values().stream()
                .sorted(Comparator.comparingInt((Report r) -> CATEGORIES.indexOf(r.category()))
                        .thenComparing(Report::title))
                .map(r -> new ReportInfo(r.id(), r.title(), r.description(), r.category(),
                        r.filters(tenantId, Map.of())))
                .toList();
    }

    /** Filters again for the values already chosen (e.g. a different plan's departments). */
    @Transactional(readOnly = true)
    public List<Filter> filters(UUID tenantId, String id, Map<String, String> params) {
        currentUser.require();
        return report(id).filters(tenantId, clean(params));
    }

    @Transactional(readOnly = true)
    public ReportResult run(UUID tenantId, String id, Map<String, String> params) {
        currentUser.require();
        return report(id).run(tenantId, clean(params));
    }

    /** The report as an Excel workbook; each export is recorded in the audit trail. */
    @Transactional
    public byte[] xlsx(UUID tenantId, String id, Map<String, String> params) {
        currentUser.require();
        Map<String, String> p = clean(params);
        ReportResult r = report(id).run(tenantId, p);
        audit.record(tenantId, "report.exported", "Report", tenantId,
                JsonText.object("report", id, "rows", r.rows().size(), "filters", p.toString()));
        return XlsxWriter.write(r);
    }

    private Report report(String id) {
        Report r = reports.get(id);
        if (r == null) {
            throw new NotFoundException("There is no report called " + id);
        }
        return r;
    }

    /** Request parameters as given, without empty values' surrounding spaces; at most 20 kept. */
    private static Map<String, String> clean(Map<String, String> params) {
        Map<String, String> out = new HashMap<>();
        if (params != null) {
            params.entrySet().stream().limit(20).forEach(e -> out.put(e.getKey(),
                    e.getValue() == null ? "" : e.getValue().trim()));
        }
        return out;
    }
}
