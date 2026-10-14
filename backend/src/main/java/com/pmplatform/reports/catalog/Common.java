package com.pmplatform.reports.catalog;

import com.pmplatform.portfolio.Project;
import com.pmplatform.portfolio.ProjectService;
import com.pmplatform.reports.ReportModel.Filter;
import com.pmplatform.reports.ReportModel.FilterType;
import com.pmplatform.reports.ReportModel.Option;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Filters several reports share. */
final class Common {

    private Common() {
    }

    static Filter projectFilter(ProjectService projects, UUID tenantId) {
        List<Option> options = new ArrayList<>();
        options.add(new Option("", "All projects"));
        projects.list(tenantId, null).stream().sorted(Comparator.comparing(Project::getCode))
                .forEach(p -> options.add(new Option(p.getId().toString(), p.getCode() + " · " + p.getName())));
        return new Filter("projectId", "Project", FilterType.SELECT, options, "");
    }

    /** "All" first, then the given options. */
    static List<Option> withAll(String allLabel, List<Option> options) {
        List<Option> out = new ArrayList<>();
        out.add(new Option("", allLabel));
        out.addAll(options);
        return out;
    }
}
