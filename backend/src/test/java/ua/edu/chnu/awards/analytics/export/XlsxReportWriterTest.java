package ua.edu.chnu.awards.analytics.export;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.edu.chnu.awards.analytics.export.UnitAnalyticsFixture.INJECTED;
import static ua.edu.chnu.awards.analytics.export.UnitAnalyticsFixture.department;
import static ua.edu.chnu.awards.analytics.export.UnitAnalyticsFixture.faculty;
import static ua.edu.chnu.awards.analytics.export.UnitAnalyticsFixture.report;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.CellType;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;
import org.junit.jupiter.api.Test;

class XlsxReportWriterTest {

    private final XlsxReportWriter writer = new XlsxReportWriter();

    @Test
    void ac3_3_oneSheetPerSectionAfterTheReportSheet() throws IOException {
        UnitReport report = report(faculty(), ReportLanguage.UK);

        try (ReadableWorkbook workbook = open(writer.write(report))) {
            List<String> names = workbook.getSheets().map(Sheet::getName).toList();

            assertThat(writer.format()).isEqualTo(ReportFormat.XLSX);
            assertThat(names).hasSize(1 + report.sections().size());
            assertThat(names.subList(1, names.size())).containsExactlyElementsOf(report.sections().stream()
                .map(section -> section.title().substring(0, Math.min(31, section.title().length()))).toList());
            List<Row> first = workbook.getFirstSheet().read();
            assertThat(first.getFirst().getCellText(0)).isEqualTo(report.title());
            assertThat(first).anyMatch(row -> "Факультет математики та інформатики".equals(row.getCellText(1)));
            assertThat(first.getLast().getCellText(0)).isEqualTo(report.note());
        }
    }

    @Test
    void ac3_3_sectionSheetsHaveAHeaderRowAndNumberCells() throws IOException {
        UnitReport report = report(faculty(), ReportLanguage.EN);

        try (ReadableWorkbook workbook = open(writer.write(report))) {
            List<Row> totals = sheet(workbook, 1);

            assertThat(totals.getFirst().stream().map(Cell::getText).toList())
                .containsExactlyElementsOf(report.columns());
            Row approved = totals.get(1);
            assertThat(approved.getCellText(1)).isEqualTo("Approved awards");
            assertThat(approved.getCell(3).getType()).isEqualTo(CellType.NUMBER);
            assertThat(approved.getCell(3).asNumber()).isEqualByComparingTo(BigDecimal.valueOf(50));
            assertThat(approved.getCell(4).asNumber()).isEqualByComparingTo(BigDecimal.valueOf(40));
            List<Row> byYear = sheet(workbook, 2);
            assertThat(byYear.get(1).getCell(2).getType()).isEqualTo(CellType.NUMBER);
            assertThat(byYear.get(1).getCell(2).asNumber()).isEqualByComparingTo(BigDecimal.valueOf(2022));
        }
    }

    @Test
    void ac3_3_textCellsStartingLikeAFormulaAreDefused() throws IOException {
        try (ReadableWorkbook workbook = open(writer.write(report(faculty(), ReportLanguage.EN)))) {
            List<Row> sources = sheet(workbook, 6);

            assertThat(sources.get(1).getCell(1).getType()).isEqualTo(CellType.STRING);
            assertThat(sources.get(1).getCellText(1)).isEqualTo("'" + INJECTED);
        }
    }

    @Test
    void ac3_2_anEmptySectionSaysSo() throws IOException {
        UnitReport report = report(department(), ReportLanguage.EN);

        try (ReadableWorkbook workbook = open(writer.write(report))) {
            List<Row> departments = sheet(workbook, 3);

            assertThat(departments).hasSize(2);
            assertThat(departments.get(1).getCellText(1)).isEqualTo(report.empty());
        }
    }

    private static ReadableWorkbook open(byte[] file) throws IOException {
        return new ReadableWorkbook(new ByteArrayInputStream(file));
    }

    private static List<Row> sheet(ReadableWorkbook workbook, int index) throws IOException {
        return workbook.getSheet(index).orElseThrow().read();
    }
}
