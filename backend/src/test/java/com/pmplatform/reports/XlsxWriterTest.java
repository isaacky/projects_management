package com.pmplatform.reports;

import static org.assertj.core.api.Assertions.assertThat;

import com.pmplatform.reports.ReportModel.Column;
import com.pmplatform.reports.ReportModel.ColumnType;
import com.pmplatform.reports.ReportModel.ReportResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class XlsxWriterTest {

    private static Map<String, String> unzip(byte[] bytes) throws IOException {
        Map<String, String> out = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                out.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    @Test
    void columnLettersEscapingAndSheetNames() {
        assertThat(XlsxWriter.column(0)).isEqualTo("A");
        assertThat(XlsxWriter.column(25)).isEqualTo("Z");
        assertThat(XlsxWriter.column(26)).isEqualTo("AA");
        assertThat(XlsxWriter.column(701)).isEqualTo("ZZ");
        assertThat(XlsxWriter.column(702)).isEqualTo("AAA");
        assertThat(XlsxWriter.escape("a<b & \"c\"\u0001")).isEqualTo("a&lt;b &amp; &quot;c&quot;");
        assertThat(XlsxWriter.sheetName("Plan: 2026/27 [draft]")).isEqualTo("Plan  2026 27  draft");
        assertThat(XlsxWriter.sheetName("A very long report title that goes on and on")).hasSize(31);
    }

    @Test
    void writesTypedCellsTotalsAndNotes() throws IOException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", "Transformers & cables");
        row.put("amount", 123_456_789L);
        row.put("pct", new BigDecimal("42.50"));
        row.put("due", LocalDate.of(2026, 10, 11));
        row.put("count", 3);
        ReportResult r = new ReportResult("x", "Contract register", "All contracts", "KES",
                List.of(new Column("name", "Name", ColumnType.TEXT), new Column("amount", "Value", ColumnType.MONEY),
                        new Column("pct", "Done", ColumnType.PERCENT), new Column("due", "Due", ColumnType.DATE),
                        new Column("count", "Count", ColumnType.INTEGER)),
                List.of(row), Map.of("amount", 123_456_789L), List.of("A note"), Instant.parse("2026-10-11T16:00:00Z"));
        Map<String, String> parts = unzip(XlsxWriter.write(r));
        assertThat(parts).containsKeys("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml");
        String sheet = parts.get("xl/worksheets/sheet1.xml");
        assertThat(sheet).contains("Transformers &amp; cables");
        assertThat(sheet).contains("<c r=\"B6\" s=\"2\"><v>1234567.89</v></c>");      // money in shillings
        assertThat(sheet).contains("<c r=\"C6\" s=\"4\"><v>42.5</v></c>");            // percent
        assertThat(sheet).contains("<c r=\"D6\" s=\"3\"><v>46306</v></c>");           // Excel date serial
        assertThat(sheet).contains("<c r=\"A7\" s=\"9\" t=\"inlineStr\"><is><t xml:space=\"preserve\">Total");
        assertThat(sheet).contains("<c r=\"B7\" s=\"10\"><v>1234567.89</v></c>");
        assertThat(sheet).contains("A note");
        assertThat(sheet).contains("<autoFilter ref=\"A5:E6\"/>");
        assertThat(sheet).contains("state=\"frozen\"");
        assertThat(parts.get("xl/workbook.xml")).contains("name=\"Contract register\"");
    }
}
