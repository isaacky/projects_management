package com.pmplatform.reports;

import com.pmplatform.reports.ReportModel.Column;
import com.pmplatform.reports.ReportModel.ColumnType;
import com.pmplatform.reports.ReportModel.ReportResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a report as a single-sheet Excel workbook (Office Open XML), with no library: a title,
 * a bold header row with filters and a frozen pane, typed cells (money in shillings with two
 * decimals, real dates, percentages) and a bold totals row.
 */
public final class XlsxWriter {

    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);
    private static final int HEADER_ROW = 5;

    // Cell style indexes in styles.xml (cellXfs).
    private static final int S_TEXT = 0;
    private static final int S_HEADER = 1;
    private static final int S_MONEY = 2;
    private static final int S_DATE = 3;
    private static final int S_PCT = 4;
    private static final int S_NUMBER = 5;
    private static final int S_INT = 6;
    private static final int S_TITLE = 7;
    private static final int S_NOTE = 8;
    private static final int S_TOTAL_TEXT = 9;
    private static final int S_TOTAL_MONEY = 10;
    private static final int S_TOTAL_INT = 11;
    private static final int S_TOTAL_NUMBER = 12;
    private static final int S_HEADER_RIGHT = 13;

    private XlsxWriter() {
    }

    public static byte[] write(ReportResult r) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            put(zip, "[Content_Types].xml", CONTENT_TYPES);
            put(zip, "_rels/.rels", ROOT_RELS);
            put(zip, "xl/workbook.xml", workbook(r));
            put(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS);
            put(zip, "xl/styles.xml", STYLES);
            put(zip, "xl/worksheets/sheet1.xml", sheet(r));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    // ------------------------------------------------------------------ sheet

    static String sheet(ReportResult r) {
        List<Column> cols = r.columns();
        int n = cols.size();
        StringBuilder rows = new StringBuilder();
        int[] width = new int[n];
        for (int c = 0; c < n; c++) {
            width[c] = Math.max(8, cols.get(c).label().length() + 2);
        }

        String generated = "Generated " + DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
                .format(r.generatedAt().atZone(ZoneId.of("Africa/Nairobi")));
        rows.append("<row r=\"1\">").append(text("A1", r.title(), S_TITLE)).append("</row>");
        if (r.subtitle() != null && !r.subtitle().isBlank()) {
            rows.append("<row r=\"2\">").append(text("A2", r.subtitle(), S_TEXT)).append("</row>");
        }
        rows.append("<row r=\"3\">").append(text("A3", generated, S_NOTE)).append("</row>");

        rows.append("<row r=\"").append(HEADER_ROW).append("\">");
        for (int c = 0; c < n; c++) {
            ColumnType t = cols.get(c).type();
            rows.append(text(ref(c, HEADER_ROW), cols.get(c).label(),
                    t == ColumnType.TEXT || t == ColumnType.DATE ? S_HEADER : S_HEADER_RIGHT));
        }
        rows.append("</row>");

        int rowNo = HEADER_ROW;
        for (Map<String, Object> row : r.rows()) {
            rowNo++;
            rows.append("<row r=\"").append(rowNo).append("\">");
            for (int c = 0; c < n; c++) {
                Column col = cols.get(c);
                Object v = row.get(col.key());
                rows.append(cell(ref(c, rowNo), col.type(), v, false));
                width[c] = Math.max(width[c], Math.min(60, display(col.type(), v).length() + 2));
            }
            rows.append("</row>");
        }
        int lastData = rowNo;
        if (r.totals() != null && !r.totals().isEmpty()) {
            rowNo++;
            rows.append("<row r=\"").append(rowNo).append("\">");
            boolean labelled = false;
            for (int c = 0; c < n; c++) {
                Column col = cols.get(c);
                Object v = r.totals().get(col.key());
                if (v == null && !labelled && c == 0) {
                    rows.append(text(ref(c, rowNo), "Total", S_TOTAL_TEXT));
                    labelled = true;
                } else if (v != null) {
                    rows.append(cell(ref(c, rowNo), col.type(), v, true));
                }
            }
            rows.append("</row>");
        }
        if (r.notes() != null) {
            rowNo++;
            for (String note : r.notes()) {
                rowNo++;
                rows.append("<row r=\"").append(rowNo).append("\">").append(text(ref(0, rowNo), note, S_NOTE))
                        .append("</row>");
            }
        }

        StringBuilder colsXml = new StringBuilder(n == 0 ? "" : "<cols>");
        for (int c = 0; c < n; c++) {
            colsXml.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1).append("\" width=\"")
                    .append(width[c]).append("\" customWidth=\"1\"/>");
        }
        if (n > 0) {
            colsXml.append("</cols>"); // <cols> must hold at least one <col>
        }
        String lastCol = column(Math.max(0, n - 1));
        return XML_HEAD
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<dimension ref=\"A1:" + lastCol + Math.max(rowNo, HEADER_ROW) + "\"/>"
                + "<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"" + HEADER_ROW + "\" topLeftCell=\"A"
                + (HEADER_ROW + 1) + "\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>"
                + "<sheetFormatPr defaultRowHeight=\"15\"/>"
                + colsXml
                + "<sheetData>" + rows + "</sheetData>"
                + (n > 0 ? "<autoFilter ref=\"A" + HEADER_ROW + ":" + lastCol + Math.max(lastData, HEADER_ROW) + "\"/>"
                        : "")
                + "<pageMargins left=\"0.5\" right=\"0.5\" top=\"0.75\" bottom=\"0.75\" header=\"0.3\" footer=\"0.3\"/>"
                + "<pageSetup orientation=\"landscape\" fitToHeight=\"0\"/>"
                + "</worksheet>";
    }

    private static String cell(String ref, ColumnType type, Object v, boolean total) {
        if (v == null) {
            return "";
        }
        switch (type) {
            case MONEY -> {
                if (v instanceof Number num) {
                    BigDecimal major = BigDecimal.valueOf(num.longValue(), 2);
                    return number(ref, major.toPlainString(), total ? S_TOTAL_MONEY : S_MONEY);
                }
            }
            case INTEGER -> {
                if (v instanceof Number num) {
                    return number(ref, Long.toString(num.longValue()), total ? S_TOTAL_INT : S_INT);
                }
            }
            case NUMBER -> {
                if (v instanceof Number num) {
                    return number(ref, plain(num), total ? S_TOTAL_NUMBER : S_NUMBER);
                }
            }
            case PERCENT -> {
                if (v instanceof Number num) {
                    return number(ref, plain(num), S_PCT);
                }
            }
            case DATE -> {
                if (v instanceof LocalDate d) {
                    return number(ref, Long.toString(ChronoUnit.DAYS.between(EXCEL_EPOCH, d)), S_DATE);
                }
            }
            default -> {
            }
        }
        return text(ref, String.valueOf(v), total ? S_TOTAL_TEXT : S_TEXT);
    }

    private static String plain(Number n) {
        return n instanceof BigDecimal b ? b.stripTrailingZeros().toPlainString() : n.toString();
    }

    private static String display(ColumnType type, Object v) {
        if (v == null) {
            return "";
        }
        if (type == ColumnType.MONEY && v instanceof Number n) {
            return String.format("%,.2f", n.longValue() / 100.0);
        }
        if (type == ColumnType.DATE) {
            return "00 Mmm 0000";
        }
        return String.valueOf(v);
    }

    private static String number(String ref, String value, int style) {
        return "<c r=\"" + ref + "\" s=\"" + style + "\"><v>" + value + "</v></c>";
    }

    private static String text(String ref, String value, int style) {
        return "<c r=\"" + ref + "\" s=\"" + style + "\" t=\"inlineStr\"><is><t xml:space=\"preserve\">"
                + escape(value) + "</t></is></c>";
    }

    static String ref(int col, int row) {
        return column(col) + row;
    }

    /** 0 → A, 25 → Z, 26 → AA. */
    static String column(int index) {
        StringBuilder s = new StringBuilder();
        int i = index;
        do {
            s.insert(0, (char) ('A' + i % 26));
            i = i / 26 - 1;
        } while (i >= 0);
        return s.toString();
    }

    /** XML-escapes text and drops characters XML 1.0 does not allow. */
    static String escape(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                default -> {
                    if (Character.isHighSurrogate(c) && i + 1 < s.length()
                            && Character.isLowSurrogate(s.charAt(i + 1))) {
                        out.append(c).append(s.charAt(++i)); // a valid pair (e.g. an emoji)
                    } else if (Character.isSurrogate(c) || c == 0xFFFE || c == 0xFFFF) {
                        // not allowed in XML 1.0: drop
                    } else if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private static String workbook(ReportResult r) {
        String name = escape(sheetName(r.title()));
        return XML_HEAD + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets><sheet name=\"" + name + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
                + (r.columns().isEmpty() ? "" : "<definedNames><definedName name=\"_xlnm._FilterDatabase\" "
                        + "localSheetId=\"0\" hidden=\"1\">'" + name.replace("'", "''") + "'!$A$" + HEADER_ROW + ":$"
                        + column(r.columns().size() - 1) + "$" + Math.max(HEADER_ROW + r.rows().size(), HEADER_ROW)
                        + "</definedName></definedNames>")
                + "</workbook>";
    }

    /** Sheet names: at most 31 characters, none of : \ / ? * [ ]. */
    static String sheetName(String title) {
        String s = title == null || title.isBlank() ? "Report" : title.replaceAll("[:\\\\/?*\\[\\]]", " ").trim();
        return s.length() > 31 ? s.substring(0, 31).trim() : s;
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static final String XML_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n";

    private static final String CONTENT_TYPES = XML_HEAD
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/xl/workbook.xml\" "
            + "ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" "
            + "ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
            + "<Override PartName=\"/xl/styles.xml\" "
            + "ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
            + "</Types>";

    private static final String ROOT_RELS = XML_HEAD
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" "
            + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" "
            + "Target=\"xl/workbook.xml\"/></Relationships>";

    private static final String WORKBOOK_RELS = XML_HEAD
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" "
            + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" "
            + "Target=\"worksheets/sheet1.xml\"/>"
            + "<Relationship Id=\"rId2\" "
            + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" "
            + "Target=\"styles.xml\"/></Relationships>";

    private static final String STYLES = XML_HEAD
            + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
            + "<numFmts count=\"3\">"
            + "<numFmt numFmtId=\"164\" formatCode=\"#,##0.00\"/>"
            + "<numFmt numFmtId=\"165\" formatCode=\"dd mmm yyyy\"/>"
            + "<numFmt numFmtId=\"166\" formatCode=\"0.0&quot;%&quot;\"/>"
            + "</numFmts>"
            + "<fonts count=\"4\">"
            + "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
            + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>"
            + "<font><b/><sz val=\"14\"/><name val=\"Calibri\"/></font>"
            + "<font><i/><sz val=\"10\"/><color rgb=\"FF666666\"/><name val=\"Calibri\"/></font>"
            + "</fonts>"
            + "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill>"
            + "<fill><patternFill patternType=\"gray125\"/></fill>"
            + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFE8EEF4\"/><bgColor indexed=\"64\"/>"
            + "</patternFill></fill></fills>"
            + "<borders count=\"2\"><border><left/><right/><top/><bottom/><diagonal/></border>"
            + "<border><left/><right/><top style=\"thin\"/><bottom/><diagonal/></border></borders>"
            + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
            + "<cellXfs count=\"14\">"
            + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"                      // 0 text
            + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" "
            + "applyFill=\"1\"/>"                                                                                 // 1 header
            + "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"  // 2 money
            + "<xf numFmtId=\"165\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"  // 3 date
            + "<xf numFmtId=\"166\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"  // 4 pct
            + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"                      // 5 number
            + "<xf numFmtId=\"3\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"    // 6 integer
            + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"       // 7 title
            + "<xf numFmtId=\"0\" fontId=\"3\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"       // 8 note
            + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" "
            + "applyBorder=\"1\"/>"                                                                               // 9 total text
            + "<xf numFmtId=\"164\" fontId=\"1\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\" "
            + "applyFont=\"1\" applyBorder=\"1\"/>"                                                               // 10 total money
            + "<xf numFmtId=\"3\" fontId=\"1\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\" "
            + "applyFont=\"1\" applyBorder=\"1\"/>"                                                               // 11 total int
            + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" "
            + "applyBorder=\"1\"/>"                                                                               // 12 total number
            + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" "
            + "applyFill=\"1\" applyAlignment=\"1\"><alignment horizontal=\"right\"/></xf>"              // 13 header, right
            + "</cellXfs>"
            + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
            + "</styleSheet>";
}
