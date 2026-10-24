package ua.edu.chnu.awards.analytics.export;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

/**
 * Writes the report as one long RFC 4180 table, UTF-8 with a byte-order mark so that spreadsheets read the Cyrillic.
 */
@Component
public class CsvReportWriter implements ReportWriter {

    private static final String BOM = "﻿";
    private static final String CRLF = "\r\n";
    private static final String HEADER = "section,key,label,year,current,previous";
    private static final String REPORT = "report";

    @Override
    public ReportFormat format() {
        return ReportFormat.CSV;
    }

    @Override
    public byte[] write(UnitReport report) {
        StringBuilder csv = new StringBuilder(BOM).append(HEADER).append(CRLF);
        for (UnitReport.Field field : report.header()) {
            line(csv, REPORT, field.key(), field.value(), null, null, null);
        }
        line(csv, REPORT, "note", report.note(), null, null, null);
        for (UnitReport.Section section : report.sections()) {
            for (UnitReport.Row row : section.rows()) {
                line(csv, section.key(), row.key(), row.label(), row.year(), row.current(), row.previous());
            }
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String cell(String text) {
        String guarded = ReportTextHelper.guard(text);
        boolean quote = guarded.indexOf(',') >= 0 || guarded.indexOf('"') >= 0 || guarded.indexOf('\n') >= 0
            || guarded.indexOf('\r') >= 0;
        return quote ? '"' + guarded.replace("\"", "\"\"") + '"' : guarded;
    }

    private static void line(StringBuilder csv, String section, String key, String label, Integer year,
                             BigDecimal current, BigDecimal previous) {
        csv.append(cell(section)).append(',').append(cell(key)).append(',').append(cell(label)).append(',')
            .append(year == null ? "" : year).append(',').append(number(current)).append(',')
            .append(number(previous)).append(CRLF);
    }

    private static String number(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }
}
