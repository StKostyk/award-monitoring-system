package ua.edu.chnu.awards.analytics.export;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.edu.chnu.awards.analytics.export.UnitAnalyticsFixture.faculty;
import static ua.edu.chnu.awards.analytics.export.UnitAnalyticsFixture.report;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class CsvReportWriterTest {

    private final CsvReportWriter writer = new CsvReportWriter();

    @Test
    void ac3_3_theFileIsUtf8WithAByteOrderMarkAndOneLongTable() {
        byte[] file = writer.write(report(faculty(), ReportLanguage.UK));

        assertThat(writer.format()).isEqualTo(ReportFormat.CSV);
        assertThat(Arrays.copyOf(file, 3)).containsExactly(0xEF, 0xBB, 0xBF);
        List<String> lines = lines(file);
        assertThat(lines.getFirst()).isEqualTo("﻿section,key,label,year,current,previous");
        assertThat(lines).contains(
            "report,unit,Факультет математики та інформатики,,,",
            "totals,approved,Затверджені нагороди,,50,40",
            "byYear,total,Затверджені нагороди,2022,10,",
            "totals,perHundredMembers,Нагород на 100 працівників,,50.0,");
        assertThat(lines).anyMatch(line -> line.startsWith("report,note,"));
    }

    @Test
    void ac3_2_everySectionAppearsWithItsRows() {
        UnitReport report = report(faculty(), ReportLanguage.EN);
        List<String> lines = lines(writer.write(report));

        long rows = report.sections().stream().mapToLong(section -> section.rows().size()).sum();
        assertThat(lines).hasSize(1 + report.header().size() + 1 + (int) rows);
        report.sections().forEach(section -> assertThat(lines).anyMatch(line -> line.startsWith(section.key() + ",")));
    }

    @Test
    void ac3_3_fieldsAreQuotedAfterRfc4180AndFormulasAreDefused() {
        List<String> lines = lines(writer.write(report(faculty(), ReportLanguage.EN)));

        assertThat(lines).contains("bySource,1,\"'=HYPERLINK(\"\"http://example.org\"\")\",,4,");
        assertThat(lines).anyMatch(line -> line.startsWith(
            "topPerformers,1,\"Petrenko Ivan1, Department 1, highest level: International\",,6,"));
        assertThat(ReportTextHelper.guard("+380")).isEqualTo("'+380");
        assertThat(ReportTextHelper.guard("-1")).isEqualTo("'-1");
        assertThat(ReportTextHelper.guard("@sum")).isEqualTo("'@sum");
        assertThat(ReportTextHelper.guard("\tx")).isEqualTo("'\tx");
        assertThat(ReportTextHelper.guard("\rx")).isEqualTo("'\rx");
        assertThat(ReportTextHelper.guard(null)).isEmpty();
        assertThat(CsvReportWriter.cell("a\nb")).isEqualTo("\"a\nb\"");
    }

    @Test
    void ac3_3_recordsEndWithCrLf() {
        String text = new String(writer.write(report(faculty(), ReportLanguage.EN)), StandardCharsets.UTF_8);

        assertThat(text).endsWith("\r\n").doesNotContain(",\n");
    }

    private static List<String> lines(byte[] file) {
        return List.of(new String(file, StandardCharsets.UTF_8).split("\r\n"));
    }
}
