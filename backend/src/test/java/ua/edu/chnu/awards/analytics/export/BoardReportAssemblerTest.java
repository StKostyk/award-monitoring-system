package ua.edu.chnu.awards.analytics.export;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.edu.chnu.awards.analytics.export.ComplianceFixture.compliance;
import static ua.edu.chnu.awards.analytics.export.ComplianceFixture.quietCompliance;
import static ua.edu.chnu.awards.analytics.export.UniversityReportFixture.LONG_NAME;
import static ua.edu.chnu.awards.analytics.export.UniversityReportFixture.empty;
import static ua.edu.chnu.awards.analytics.export.UniversityReportFixture.report;
import static ua.edu.chnu.awards.analytics.export.UniversityReportFixture.university;

import java.util.List;

import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.analytics.dto.TrendProjection;

class BoardReportAssemblerTest {

    @Test
    void ac3_2_thePagesFollowTheTopicOrder() {
        BoardReport report = report(university(), compliance(), ReportLanguage.EN);

        assertThat(report.pages()).extracting(BoardReport.Page::key).containsExactly("figures", "trend",
            "comparison", "categories", "trends", "highlights", "compliance");
        assertThat(report.title()).isEqualTo("Board report: Yuriy Fedkovych Chernivtsi National University");
        assertThat(report.header()).extracting(UnitReport.Field::value).contains("2022–2026", "2017–2021",
            "2026-10-25 15:30", "Ivan Rector", "2026-10-25 14:00");
        assertThat(report.note()).contains("Nobody is named");
    }

    @Test
    void ac3_2_keyFiguresCarryTheDeltasOfTheAnalytics() {
        BoardReport.Table figures = page(report(university(), compliance(), ReportLanguage.EN), "figures")
            .tables().getFirst();

        assertThat(figures.columns()).containsExactly("Figure", "This period", "Previous period", "Change");
        assertThat(figures.rows().getFirst())
            .isEqualTo(new BoardReport.Row("Approved awards", List.of("120", "100", "+20 (+20.0 %)")));
        assertThat(figures.rows()).contains(new BoardReport.Row("Awarded people", List.of("70", "64", "+6 (+9.4 %)")),
            new BoardReport.Row("Awards per 100 members", List.of("54.5", "", "")));
    }

    @Test
    void ac3_3_barsAreSizedOverTheLargestValue() {
        BoardReport report = report(university(), compliance(), ReportLanguage.EN);
        BoardReport.Chart years = page(report, "trend").charts().getFirst();
        BoardReport.Chart faculties = page(report, "comparison").charts().getFirst();

        assertThat(years.bars()).extracting(BoardReport.Bar::width).containsExactly(85, 88, 92, 96, 100);
        assertThat(years.bars().getLast()).isEqualTo(new BoardReport.Bar("2026", "26", 100));
        assertThat(faculties.title()).isEqualTo("Awards per 100 members");
        assertThat(faculties.bars()).hasSize(8);
        assertThat(faculties.bars().getFirst()).isEqualTo(new BoardReport.Bar("Faculty 1", "40.0", 36));
        assertThat(faculties.bars().getLast()).isEqualTo(new BoardReport.Bar("Faculty 8", "110.0", 100));
        assertThat(faculties.bars()).extracting(BoardReport.Bar::label).contains(LONG_NAME + " (inactive)");
    }

    @Test
    void ac3_2_theComparisonTableListsEveryRowWithTheUniversityLevel() {
        BoardReport.Table table = page(report(university(), compliance(), ReportLanguage.EN), "comparison")
            .tables().getFirst();

        assertThat(table.rows()).hasSize(9);
        assertThat(table.rows().getFirst())
            .isEqualTo(new BoardReport.Row("University level", List.of("3", "1", "0", "+1", "—", "0")));
        assertThat(table.rows().get(1))
            .isEqualTo(new BoardReport.Row("Faculty 1", List.of("20", "10", "8", "+2 (+25.0 %)", "40.0", "7")));
    }

    @Test
    void ac3_2_theProjectionIsASentenceOrTheTooFewYearsLine() {
        BoardReport projected = report(university(), compliance(), ReportLanguage.EN);
        BoardReport tooFew = report(university(3, TrendProjection.TOO_FEW_YEARS), compliance(), ReportLanguage.EN);

        assertThat(page(projected, "trend").notes())
            .contains("Projected for 2027: about 31 awards, based on 2022–2025.");
        assertThat(page(tooFew, "trend").notes()).contains("Too few complete years for a projection.");
    }

    @Test
    void ac3_2_categoriesTrendsAndHighlightsUseTheUniversityWording() {
        BoardReport report = report(university(), compliance(), ReportLanguage.EN);

        assertThat(page(report, "categories").tables()).extracting(BoardReport.Table::title)
            .containsExactly("By category", "By level");
        assertThat(page(report, "categories").tables().get(1).rows().getFirst())
            .isEqualTo(new BoardReport.Row("National", List.of("70", "60", "+10 (+16.7 %)")));
        List<BoardReport.Table> trends = page(report, "trends").tables();
        assertThat(trends.getFirst().rows().getFirst())
            .isEqualTo(new BoardReport.Row("Category 1", List.of("11", "5", "+6 (+120.0 %)")));
        assertThat(trends.get(1).rows()).isEmpty();
        assertThat(trends.get(1).empty()).isEqualTo("No category changed noticeably.");
        assertThat(trends.get(3).rows().getFirst())
            .isEqualTo(new BoardReport.Row("Ministry of Education", List.of("2", "9", "−7 (−77.8 %)")));
        List<BoardReport.Table> highlights = page(report, "highlights").tables();
        assertThat(highlights.getFirst().rows()).extracting(BoardReport.Row::label).containsExactly(
            "Faculty 8: 17 awards, at least one and a half times the university's average per member");
        assertThat(highlights.get(1).rows()).extracting(BoardReport.Row::label).containsExactly(
            "Faculty 1: 10 awards, under half of the university's average per member");
    }

    @Test
    void ac3_2_theComplianceSnapshotHasTheStatusReasonsAndCounts() {
        BoardReport.Page page = page(report(university(), compliance(), ReportLanguage.EN), "compliance");

        assertThat(page.notes()).containsExactly("Needs attention", "Some award reviews are overdue.",
            "Read at 2026-10-25 14:30");
        assertThat(page.tables().getFirst().rows()).contains(
            new BoardReport.Row("Privacy notice 1.2 acknowledged", List.of("180 of 220")),
            new BoardReport.Row("Analytics consents", List.of("95 of 220")),
            new BoardReport.Row("Last retention run", List.of("Completed, 2026-10-25 03:00")),
            new BoardReport.Row("Overdue reviews", List.of("2")));
        assertThat(page.tables().get(1).rows().getFirst())
            .isEqualTo(new BoardReport.Row("Faculty secretary", List.of("3", "2")));
    }

    @Test
    void ac3_2_anEmptyPeriodAndAQuietComplianceStillGiveEveryPage() {
        BoardReport report = report(empty(), quietCompliance(), ReportLanguage.EN);

        assertThat(report.pages()).hasSize(7);
        assertThat(page(report, "figures").tables().getFirst().rows())
            .contains(new BoardReport.Row("Awards per 100 members", List.of("—", "", "")));
        assertThat(page(report, "trend").charts().getFirst().bars()).extracting(BoardReport.Bar::width)
            .containsOnly(0);
        assertThat(page(report, "comparison").tables().getFirst().rows()).isEmpty();
        assertThat(page(report, "highlights").notes()).containsExactly("Nothing stands out in this period.");
        assertThat(page(report, "compliance").notes().getFirst()).isEqualTo("Everything is in order");
        assertThat(page(report, "compliance").tables().getFirst().rows())
            .contains(new BoardReport.Row("Last retention run", List.of("No run yet")));
    }

    @Test
    void ac3_3_theLabelsFollowTheLanguage() {
        BoardReport report = report(university(), compliance(), ReportLanguage.UK);

        assertThat(report.title())
            .isEqualTo("Звіт для ради: Чернівецький національний університет імені Юрія Федьковича");
        assertThat(report.pages()).extracting(BoardReport.Page::title).contains("Ключові показники",
            "Порівняння факультетів і коледжів", "Відповідність");
        assertThat(page(report, "comparison").charts().getFirst().bars().getFirst().label()).isEqualTo("Факультет 1");
        assertThat(page(report, "trend").notes())
            .contains("Прогноз на 2027: близько 31 нагород, за даними 2022–2025.");
    }

    private static BoardReport.Page page(BoardReport report, String key) {
        return report.pages().stream().filter(page -> page.key().equals(key)).findFirst().orElseThrow();
    }
}
