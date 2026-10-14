package com.pmplatform.reports;

import com.pmplatform.reports.ReportModel.Filter;
import com.pmplatform.reports.ReportModel.ReportResult;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One report in the catalogue. Implementations are Spring beans and must only read. */
public interface Report {

    /** URL-safe id, e.g. "contract-register". */
    String id();

    String title();

    String description();

    /** Catalogue group: Portfolio, Procurement, Contracts, Benefits. */
    String category();

    /** The filters to show; options may depend on the values already chosen (e.g. a plan's departments). */
    List<Filter> filters(UUID tenantId, Map<String, String> params);

    ReportResult run(UUID tenantId, Map<String, String> params);
}
