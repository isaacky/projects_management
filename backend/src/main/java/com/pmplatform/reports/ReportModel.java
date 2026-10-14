package com.pmplatform.reports;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** The shapes a report is described and returned in; one generic screen and one Excel writer show them all. */
public final class ReportModel {

    private ReportModel() {
    }

    /** How a column is formatted. MONEY values are minor units (cents); PERCENT values are 0-100. */
    public enum ColumnType { TEXT, MONEY, NUMBER, INTEGER, PERCENT, DATE }

    public record Column(String key, String label, ColumnType type) {
    }

    public enum FilterType { SELECT, DATE }

    public record Option(String value, String label) {
    }

    /** A filter the screen shows; {@code defaultValue} is used when the request leaves it out. */
    public record Filter(String key, String label, FilterType type, List<Option> options, String defaultValue) {
    }

    public record ReportInfo(String id, String title, String description, String category, List<Filter> filters) {
    }

    /**
     * A report's output. Rows map column keys to values (String, Number, LocalDate or null); a row may
     * also carry "_link", the app path to open when it is clicked. Totals use the same keys.
     */
    public record ReportResult(String id, String title, String subtitle, String currency, List<Column> columns,
            List<Map<String, Object>> rows, Map<String, Object> totals, List<String> notes, Instant generatedAt) {
    }
}
