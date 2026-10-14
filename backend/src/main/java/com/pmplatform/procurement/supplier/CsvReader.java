package com.pmplatform.procurement.supplier;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC 4180 CSV reader: quoted fields, doubled quotes, commas and line breaks
 * inside quotes, CRLF or LF line ends. Also accepts semicolon-separated files (as some
 * spreadsheet exports produce) by detecting the separator from the header line.
 */
public final class CsvReader {

    private CsvReader() {
    }

    public static List<List<String>> parse(String text) {
        String s = text.startsWith("﻿") ? text.substring(1) : text; // drop Excel's BOM
        char sep = detectSeparator(s);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < s.length() && s.charAt(i + 1) == '"') {
                        field.append('"');
                        i += 2;
                        continue;
                    }
                    quoted = false;
                } else {
                    field.append(c);
                }
                i++;
                continue;
            }
            if (c == '"' && field.isEmpty()) {
                quoted = true;
            } else if (c == sep) {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                row.add(field.toString());
                field.setLength(0);
                addIfNotBlank(rows, row);
                row = new ArrayList<>();
                if (c == '\r' && i + 1 < s.length() && s.charAt(i + 1) == '\n') {
                    i++;
                }
            } else {
                field.append(c);
            }
            i++;
        }
        if (quoted) {
            throw new IllegalArgumentException("The file ends inside a quoted field; check for a missing \"");
        }
        row.add(field.toString());
        addIfNotBlank(rows, row);
        return rows;
    }

    private static void addIfNotBlank(List<List<String>> rows, List<String> row) {
        if (row.stream().anyMatch(f -> !f.isBlank())) {
            rows.add(row);
        }
    }

    private static char detectSeparator(String s) {
        int end = s.indexOf('\n');
        String header = end < 0 ? s : s.substring(0, end);
        long commas = header.chars().filter(ch -> ch == ',').count();
        long semis = header.chars().filter(ch -> ch == ';').count();
        return semis > commas ? ';' : ',';
    }
}
