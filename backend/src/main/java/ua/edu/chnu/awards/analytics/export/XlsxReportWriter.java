package ua.edu.chnu.awards.analytics.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.springframework.stereotype.Component;

/**
 * Writes the report as an Excel workbook: a first sheet with the header and the note, then one sheet per section with
 * a header row and number cells.
 */
@Component
public class XlsxReportWriter implements ReportWriter {

    private static final String APPLICATION = "Award Monitoring";
    private static final String VERSION = "1.0";
    private static final int MAX_SHEET_NAME = 31;
    private static final String SHEET_NAME_FORBIDDEN = "[\\[\\]:*?/\\\\]";
    private static final int LABEL_WIDTH = 60;
    private static final int FIELD_WIDTH = 24;
    private static final int YEAR = 2;
    private static final int CURRENT = 3;
    private static final int PREVIOUS = 4;

    @Override
    public ReportFormat format() {
        return ReportFormat.XLSX;
    }

    @Override
    public byte[] write(UnitReport report) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Workbook workbook = new Workbook(out, APPLICATION, VERSION);
            Set<String> names = new HashSet<>();
            writeHeader(workbook.newWorksheet(sheetName(report.title(), names, "report")), report);
            for (UnitReport.Section section : report.sections()) {
                writeSection(workbook.newWorksheet(sheetName(section.title(), names, section.key())), report,
                    section);
            }
            workbook.finish();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static void writeHeader(Worksheet sheet, UnitReport report) {
        sheet.value(0, 0, ReportTextHelper.guard(report.title()));
        sheet.style(0, 0).bold().set();
        int row = 1;
        for (UnitReport.Field field : report.header()) {
            sheet.value(row, 0, ReportTextHelper.guard(field.label()));
            sheet.value(row, 1, ReportTextHelper.guard(field.value()));
            row++;
        }
        sheet.value(row, 0, ReportTextHelper.guard(report.note()));
        sheet.width(0, FIELD_WIDTH);
        sheet.width(1, LABEL_WIDTH);
    }

    private static void writeSection(Worksheet sheet, UnitReport report, UnitReport.Section section) {
        List<String> columns = report.columns();
        for (int column = 0; column < columns.size(); column++) {
            sheet.value(0, column, ReportTextHelper.guard(columns.get(column)));
        }
        sheet.range(0, 0, 0, columns.size() - 1).style().bold().set();
        sheet.freezePane(0, 1);
        sheet.width(1, LABEL_WIDTH);
        if (section.rows().isEmpty()) {
            sheet.value(1, 1, ReportTextHelper.guard(report.empty()));
            return;
        }
        int row = 1;
        for (UnitReport.Row line : section.rows()) {
            sheet.value(row, 0, ReportTextHelper.guard(line.key()));
            sheet.value(row, 1, ReportTextHelper.guard(line.label()));
            if (line.year() != null) {
                sheet.value(row, YEAR, line.year());
            }
            number(sheet, row, CURRENT, line.current());
            number(sheet, row, PREVIOUS, line.previous());
            row++;
        }
    }

    private static void number(Worksheet sheet, int row, int column, BigDecimal value) {
        if (value != null) {
            sheet.value(row, column, value);
        }
    }

    private static String sheetName(String title, Set<String> taken, String fallback) {
        String name = title == null || title.isBlank() ? fallback : title.replaceAll(SHEET_NAME_FORBIDDEN, " ");
        name = name.substring(0, Math.min(MAX_SHEET_NAME, name.length())).strip();
        String unique = name;
        for (int i = 2; !taken.add(unique.toLowerCase(Locale.ROOT)); i++) {
            String suffix = " " + i;
            unique = name.substring(0, Math.min(MAX_SHEET_NAME - suffix.length(), name.length())) + suffix;
        }
        return unique;
    }
}
