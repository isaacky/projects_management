package com.pmplatform.reports;

import com.pmplatform.reports.ReportModel.Column;
import com.pmplatform.reports.ReportModel.ColumnType;
import com.pmplatform.reports.ReportModel.Option;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Small helpers for building report rows, totals and filter values. */
public final class Rows {

    private Rows() {
    }

    public static Column col(String key, String label, ColumnType type) {
        return new Column(key, label, type);
    }

    /** A row from key/value pairs, keeping column order. */
    public static Map<String, Object> row(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            m.put((String) keyValues[i], keyValues[i + 1]);
        }
        return m;
    }

    /** Sums the given numeric columns over the rows (nulls skipped; a column with no values gets no total). */
    public static Map<String, Object> totals(List<Map<String, Object>> rows, String... keys) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String k : keys) {
            long sum = 0;
            boolean any = false;
            for (Map<String, Object> r : rows) {
                if (r.get(k) instanceof Number n) {
                    sum += n.longValue();
                    any = true;
                }
            }
            if (any) {
                out.put(k, sum);
            }
        }
        return out;
    }

    /**
     * A filter value as one of an enum's constants; null when blank. An unknown value (an old
     * bookmark, a hand-edited link) is refused rather than failing with a server error.
     */
    public static <E extends Enum<E>> E choice(Map<String, String> params, String key, Class<E> type) {
        String v = str(params, key);
        if (v == null) {
            return null;
        }
        for (E e : type.getEnumConstants()) {
            if (e.name().equals(v)) {
                return e;
            }
        }
        throw new com.pmplatform.platform.web.BusinessRuleException("\"" + v + "\" is not a valid " + key);
    }

    /** As {@link #choice}, but a missing parameter (as opposed to a blank one, meaning "all") gives the default. */
    public static <E extends Enum<E>> E choice(Map<String, String> params, String key, Class<E> type, E dflt) {
        return params.containsKey(key) ? choice(params, key, type) : dflt;
    }

    public static String text(Enum<?> e) {
        if (e == null) {
            return null;
        }
        String s = e.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static List<Option> options(Enum<?>... values) {
        List<Option> out = new ArrayList<>();
        for (Enum<?> e : values) {
            out.add(new Option(e.name(), text(e)));
        }
        return out;
    }

    public static UUID uuid(Map<String, String> params, String key) {
        String v = params.get(key);
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(v.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static LocalDate date(Map<String, String> params, String key) {
        String v = params.get(key);
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(v.trim());
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    public static String str(Map<String, String> params, String key) {
        String v = params.get(key);
        return v == null || v.isBlank() ? null : v.trim();
    }

    /** Share of part in whole as a percentage (1 dp), or null when whole is 0. */
    public static BigDecimal pct(long part, long whole) {
        if (whole == 0) {
            return null;
        }
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(whole), 1, java.math.RoundingMode.HALF_UP);
    }
}
