package com.pmplatform.procurement.supplier;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns an uploaded supplier list (CSV) into validated rows. Column names are matched
 * loosely ("Supplier Name", "Company name" and "name" all work), so an existing export
 * can usually be imported without editing.
 */
public final class SupplierImportParser {

    public enum Field {
        NAME, KRA_PIN, REGISTRATION_NO, EMAIL, PHONE, ADDRESS, CATEGORIES, AGPO_CATEGORY,
        AGPO_CERTIFICATE_NO, AGPO_EXPIRY, STATUS
    }

    /** Header aliases after normalising (lower case, letters and digits only). */
    private static final Map<String, Field> ALIASES = new HashMap<>();

    static {
        alias(Field.NAME, "name", "suppliername", "companyname", "vendorname", "supplier", "vendor", "businessname", "company",
                "firmname");
        alias(Field.KRA_PIN, "krapin", "pin", "pinno", "pinnumber", "kra", "taxpin", "taxid", "krapinno");
        alias(Field.REGISTRATION_NO, "registrationno", "registrationnumber", "regno", "regnumber",
                "certificateofincorporation", "incorporationno", "businessregno", "cr12no");
        alias(Field.EMAIL, "email", "emailaddress", "email1", "contactemail");
        alias(Field.PHONE, "phone", "phoneno", "phonenumber", "telephone", "tel", "mobile", "mobileno",
                "contactphone");
        alias(Field.ADDRESS, "address", "postaladdress", "physicaladdress", "location");
        alias(Field.CATEGORIES, "categories", "category", "supplycategory", "supplycategories", "itemsupplied",
                "itemssupplied", "lineofbusiness", "businesscategory");
        alias(Field.AGPO_CATEGORY, "agpo", "agpocategory", "agpogroup", "specialgroup", "targetgroup",
                "preferencegroup");
        alias(Field.AGPO_CERTIFICATE_NO, "agpocertificate", "agpocertificateno", "agpocertno", "agponumber",
                "agpono");
        alias(Field.AGPO_EXPIRY, "agpoexpiry", "agpoexpirydate", "agpovalidto", "agpovaliduntil",
                "certificateexpiry");
        alias(Field.STATUS, "status", "supplierstatus");
    }

    private static void alias(Field f, String... names) {
        for (String n : names) {
            ALIASES.put(n, f);
        }
    }

    /** Letter, nine digits, letter (e.g. P051234567X). */
    private static final Pattern KRA_PIN = Pattern.compile("[A-Z]\\d{9}[A-Z]");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final List<DateTimeFormatter> DATES = List.of(DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/uuuu"), DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("d.M.uuuu"));

    /** One data row: line number in the file, cleaned values, and what is wrong with it. */
    public record Row(int line, Map<Field, String> values, List<String> errors, List<String> warnings) {

        public boolean valid() {
            return errors.isEmpty();
        }

        public String get(Field f) {
            return values.get(f);
        }
    }

    public record Result(Map<Field, String> columns, List<String> unmappedColumns, List<Row> rows,
            List<String> fileErrors) {
    }

    public static String normalise(String header) {
        return header.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static Result parse(String csvText) {
        List<List<String>> table;
        try {
            table = CsvReader.parse(csvText);
        } catch (IllegalArgumentException e) {
            return new Result(Map.of(), List.of(), List.of(), List.of(e.getMessage()));
        }
        if (table.isEmpty()) {
            return new Result(Map.of(), List.of(), List.of(), List.of("The file is empty"));
        }

        List<String> header = table.get(0);
        Map<Field, Integer> index = new EnumMap<>(Field.class);
        Map<Field, String> columns = new EnumMap<>(Field.class);
        List<String> unmapped = new ArrayList<>();
        for (int c = 0; c < header.size(); c++) {
            String raw = header.get(c).trim();
            Field f = ALIASES.get(normalise(raw));
            if (f != null && !index.containsKey(f)) {
                index.put(f, c);
                columns.put(f, raw);
            } else if (!raw.isEmpty()) {
                unmapped.add(raw);
            }
        }
        List<String> fileErrors = new ArrayList<>();
        if (!index.containsKey(Field.NAME)) {
            fileErrors.add("No supplier name column found (for example \"Supplier Name\")");
        }
        if (!index.containsKey(Field.KRA_PIN)) {
            fileErrors.add("No KRA PIN column found (for example \"KRA PIN\")");
        }
        if (!fileErrors.isEmpty()) {
            return new Result(columns, unmapped, List.of(), fileErrors);
        }

        List<Row> rows = new ArrayList<>();
        Set<String> seenPins = new HashSet<>();
        for (int r = 1; r < table.size(); r++) {
            List<String> cells = table.get(r);
            Map<Field, String> v = new EnumMap<>(Field.class);
            index.forEach((f, c) -> {
                String cell = c < cells.size() ? cells.get(c).trim() : "";
                if (!cell.isEmpty()) {
                    v.put(f, cell);
                }
            });
            rows.add(validate(r + 1, v, seenPins));
        }
        return new Result(columns, unmapped, rows, List.of());
    }

    private static Row validate(int line, Map<Field, String> v, Set<String> seenPins) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (v.get(Field.NAME) == null) {
            errors.add("Supplier name is missing");
        } else if (v.get(Field.NAME).length() > 200) {
            errors.add("Supplier name is longer than 200 characters");
        }

        String pin = v.get(Field.KRA_PIN);
        if (pin == null) {
            errors.add("KRA PIN is missing");
        } else {
            pin = pin.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
            v.put(Field.KRA_PIN, pin);
            if (!KRA_PIN.matcher(pin).matches()) {
                errors.add("KRA PIN \"" + pin + "\" should be a letter, 9 digits and a letter (e.g. P051234567X)");
            } else if (!seenPins.add(pin)) {
                errors.add("KRA PIN " + pin + " appears more than once in the file");
            }
        }

        String email = v.get(Field.EMAIL);
        if (email != null && !EMAIL.matcher(email).matches()) {
            warnings.add("Email \"" + email + "\" looks invalid and was left out");
            v.remove(Field.EMAIL);
        }

        String agpo = v.get(Field.AGPO_CATEGORY);
        if (agpo != null) {
            String g = agpoGroup(agpo);
            if (g == null) {
                warnings.add("AGPO category \"" + agpo + "\" not recognised (use Youth, Women or PWD); left out");
                v.remove(Field.AGPO_CATEGORY);
            } else {
                v.put(Field.AGPO_CATEGORY, g);
            }
        }

        String expiry = v.get(Field.AGPO_EXPIRY);
        if (expiry != null) {
            LocalDate d = parseDate(expiry);
            if (d == null) {
                warnings.add("AGPO expiry \"" + expiry + "\" is not a date (use 2027-06-30 or 30/06/2027); left out");
                v.remove(Field.AGPO_EXPIRY);
            } else {
                v.put(Field.AGPO_EXPIRY, d.toString());
                if (v.get(Field.AGPO_CATEGORY) != null && d.isBefore(LocalDate.now())) {
                    warnings.add("AGPO certificate expired on " + d);
                }
            }
        }

        String status = v.get(Field.STATUS);
        if (status != null) {
            String s = status.toUpperCase(Locale.ROOT).trim();
            if (s.startsWith("ACTIVE") || s.equals("APPROVED") || s.equals("REGISTERED")) {
                v.put(Field.STATUS, "ACTIVE");
            } else if (s.startsWith("SUSPEND")) {
                v.put(Field.STATUS, "SUSPENDED");
            } else if (s.startsWith("DEBAR") || s.startsWith("BLACKLIST")) {
                v.put(Field.STATUS, "DEBARRED");
            } else {
                warnings.add("Status \"" + status + "\" not recognised; imported as Active");
                v.put(Field.STATUS, "ACTIVE");
            }
        }
        return new Row(line, v, errors, warnings);
    }

    static String agpoGroup(String text) {
        String t = normalise(text);
        if (t.isEmpty() || t.equals("none") || t.equals("no") || t.equals("na")) {
            return null;
        }
        if (t.startsWith("youth") || t.equals("y")) {
            return "YOUTH";
        }
        if (t.startsWith("women") || t.startsWith("woman") || t.equals("w")) {
            return "WOMEN";
        }
        if (t.startsWith("pwd") || t.contains("disabilit") || t.startsWith("pwds")) {
            return "PWD";
        }
        return null;
    }

    static LocalDate parseDate(String text) {
        for (DateTimeFormatter f : DATES) {
            try {
                return LocalDate.parse(text.trim(), f);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        return null;
    }
}
