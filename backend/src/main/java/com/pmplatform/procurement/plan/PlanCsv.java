package com.pmplatform.procurement.plan;

import com.pmplatform.procurement.supplier.CsvReader;
import com.pmplatform.procurement.tender.ReservedGroup;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a department's plan lines from a CSV file (saved from Excel). Column names are matched
 * loosely; the method and project are resolved by the caller, which knows the tenant's settings.
 */
public final class PlanCsv {

    public enum Field {
        DESCRIPTION, CATEGORY, QUANTITY, UNIT, ESTIMATE, METHOD, QUARTER, SOURCE_OF_FUNDS, BUDGET_CODE, RESERVED,
        PROJECT
    }

    public static final int MAX_ROWS = 1000;

    private static final Map<String, Field> ALIASES = new HashMap<>();

    static {
        alias(Field.DESCRIPTION, "description", "item", "itemdescription", "descriptionofgoodsworksorservices",
                "descriptionofprocurement", "procurementitem", "subject", "goodsworksservices");
        alias(Field.CATEGORY, "category", "procurementcategory", "type", "procurementtype");
        alias(Field.QUANTITY, "quantity", "qty", "quantities");
        alias(Field.UNIT, "unit", "uom", "unitofmeasure", "unitofissue", "units");
        alias(Field.ESTIMATE, "estimate", "estimatekes", "estimateksh", "estimatedcost", "estimatedcostkes",
                "estimatedcostksh", "costkes", "costksh", "amountkes", "amountksh", "cost", "amount",
                "estimatedamount", "budget", "budgetkes", "estimatedvalue", "value", "totalcost");
        alias(Field.METHOD, "method", "procurementmethod", "methodofprocurement");
        alias(Field.QUARTER, "quarter", "plannedquarter", "timing", "period");
        alias(Field.SOURCE_OF_FUNDS, "sourceoffunds", "source", "funding", "fundsource", "sourceoffunding");
        alias(Field.BUDGET_CODE, "budgetcode", "budgetline", "votecode", "votehead", "glcode", "accountcode");
        alias(Field.RESERVED, "reserved", "agpo", "reservedfor", "reservation", "reservedgroup", "preference");
        alias(Field.PROJECT, "project", "projectcode");
    }

    private static void alias(Field f, String... names) {
        for (String n : names) {
            ALIASES.put(n, f);
        }
    }

    /** A row's values, typed where the parser can; the method and project code stay as text. */
    public record Draft(String description, PlanCategory category, BigDecimal quantity, String unit,
            Long estimateMinor, String method, Integer quarter, String sourceOfFunds, String budgetCode,
            ReservedGroup reserved, String projectCode) {
    }

    public record Row(int line, Draft draft, List<String> errors) {
    }

    public record Result(List<Row> rows, List<String> fileErrors) {
    }

    private PlanCsv() {
    }

    public static Result parse(String text) {
        List<List<String>> table;
        try {
            table = CsvReader.parse(text);
        } catch (IllegalArgumentException e) {
            return new Result(List.of(), List.of(e.getMessage()));
        }
        if (table.isEmpty()) {
            return new Result(List.of(), List.of("The file is empty"));
        }
        Map<Field, Integer> col = new EnumMap<>(Field.class);
        List<String> header = table.get(0);
        for (int i = 0; i < header.size(); i++) {
            Field f = ALIASES.get(normalise(header.get(i)));
            if (f != null && !col.containsKey(f)) {
                col.put(f, i);
            }
        }
        List<String> fileErrors = new ArrayList<>();
        for (Field required : List.of(Field.DESCRIPTION, Field.CATEGORY, Field.ESTIMATE, Field.METHOD,
                Field.QUARTER)) {
            if (!col.containsKey(required)) {
                fileErrors.add("No " + required.name().toLowerCase().replace('_', ' ') + " column");
            }
        }
        if (table.size() - 1 > MAX_ROWS) {
            fileErrors.add("At most " + MAX_ROWS + " lines in one file");
        }
        if (!fileErrors.isEmpty()) {
            return new Result(List.of(), fileErrors);
        }
        List<Row> rows = new ArrayList<>();
        for (int r = 1; r < table.size(); r++) {
            List<String> cells = table.get(r);
            List<String> errors = new ArrayList<>();
            String desc = cell(cells, col, Field.DESCRIPTION);
            if (desc == null) {
                errors.add("no description");
            }
            PlanCategory category = category(cell(cells, col, Field.CATEGORY));
            if (category == null) {
                errors.add("category must be goods, works, non-consulting services or consultancy");
            }
            BigDecimal qty = BigDecimal.ONE;
            String q = cell(cells, col, Field.QUANTITY);
            if (q != null) {
                try {
                    qty = new BigDecimal(q.replace(",", ""));
                    if (qty.signum() <= 0) {
                        errors.add("quantity must be more than zero");
                    }
                } catch (NumberFormatException e) {
                    errors.add("quantity \"" + q + "\" is not a number");
                }
            }
            String unit = cell(cells, col, Field.UNIT);
            Long estimate = amountMinor(cell(cells, col, Field.ESTIMATE));
            if (estimate == null) {
                errors.add("estimate must be an amount in KES, e.g. 1,250,000 or 1250000.50");
            }
            String method = cell(cells, col, Field.METHOD);
            if (method == null) {
                errors.add("no procurement method");
            }
            Integer quarter = quarter(cell(cells, col, Field.QUARTER));
            if (quarter == null) {
                errors.add("quarter must be 1 to 4 (or Q1 to Q4)");
            }
            String reservedText = cell(cells, col, Field.RESERVED);
            ReservedGroup reserved = null;
            if (reservedText != null) {
                reserved = reserved(reservedText);
                if (reserved == null && !isNone(reservedText)) {
                    errors.add("reserved must be youth, women, PWD, AGPO or blank");
                }
            }
            rows.add(new Row(r + 1, new Draft(desc, category, qty, unit == null ? "lot" : unit, estimate, method,
                    quarter, cell(cells, col, Field.SOURCE_OF_FUNDS), cell(cells, col, Field.BUDGET_CODE), reserved,
                    cell(cells, col, Field.PROJECT)), errors));
        }
        return new Result(rows, List.of());
    }

    static String normalise(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    static PlanCategory category(String s) {
        String n = normalise(s);
        return switch (n) {
            case "goods", "g", "supplies" -> PlanCategory.GOODS;
            case "works", "w", "civilworks" -> PlanCategory.WORKS;
            case "services", "nonconsulting", "nonconsultingservices", "nonconsultancy", "nonconsultancyservices",
                    "s", "generalservices" -> PlanCategory.NON_CONSULTING;
            case "consultancy", "consulting", "consultancyservices", "consultingservices", "c", "consultant" ->
                    PlanCategory.CONSULTANCY;
            default -> null;
        };
    }

    static Integer quarter(String s) {
        String n = normalise(s).replace("quarter", "").replace("qtr", "").replace("q", "");
        n = n.replaceAll("(st|nd|rd|th)$", "");
        return switch (n) {
            case "1" -> 1;
            case "2" -> 2;
            case "3" -> 3;
            case "4" -> 4;
            default -> null;
        };
    }

    /** "1,250,000", "KES 1 250 000.50", "Ksh. 900000" → minor units; null when not an amount. */
    static Long amountMinor(String s) {
        if (s == null) {
            return null;
        }
        String t = s.toUpperCase(Locale.ROOT).replace("KES", "").replace("KSH", "").replace(".-", "")
                .replaceAll("[\\s,\\u00a0]", "");
        if (t.startsWith(".")) {
            t = t.substring(1);
        }
        try {
            BigDecimal v = new BigDecimal(t);
            if (v.signum() < 0 || v.scale() > 2) {
                return null;
            }
            return v.movePointRight(2).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    static ReservedGroup reserved(String s) {
        return switch (normalise(s)) {
            case "youth", "y" -> ReservedGroup.YOUTH;
            case "women", "woman", "w" -> ReservedGroup.WOMEN;
            case "pwd", "pwds", "disability", "disabled", "personswithdisabilities", "personwithdisability" ->
                    ReservedGroup.PWD;
            case "agpo", "any", "all", "anyagpo", "yes" -> ReservedGroup.AGPO;
            default -> null;
        };
    }

    private static boolean isNone(String s) {
        return switch (normalise(s)) {
            case "", "no", "none", "open", "na", "n", "notreserved" -> true;
            default -> false;
        };
    }

    private static String cell(List<String> cells, Map<Field, Integer> col, Field f) {
        Integer i = col.get(f);
        if (i == null || i >= cells.size()) {
            return null;
        }
        String v = cells.get(i).trim();
        return v.isEmpty() ? null : v;
    }
}
