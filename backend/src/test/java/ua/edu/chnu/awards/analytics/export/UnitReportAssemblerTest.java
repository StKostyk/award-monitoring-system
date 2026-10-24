package ua.edu.chnu.awards.analytics.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.edu.chnu.awards.analytics.export.UnitAnalyticsFixture.INJECTED;
import static ua.edu.chnu.awards.analytics.export.UnitAnalyticsFixture.PRODUCER;
import static ua.edu.chnu.awards.analytics.export.UnitAnalyticsFixture.department;
import static ua.edu.chnu.awards.analytics.export.UnitAnalyticsFixture.faculty;
import static ua.edu.chnu.awards.analytics.export.UnitAnalyticsFixture.report;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class UnitReportAssemblerTest {

    @Test
    void ac3_2_theHeaderNamesUnitPeriodsProductionAndProducer() {
        UnitReport report = report(faculty(), ReportLanguage.EN);

        assertThat(report.title()).isEqualTo("Unit analytics: Faculty of Mathematics and Informatics");
        assertThat(report.header()).extracting(UnitReport.Field::key, UnitReport.Field::label, UnitReport.Field::value)
            .containsExactly(
                tuple("unit", "Unit", "Faculty of Mathematics and Informatics"),
                tuple("period", "Period", "2022–2026"),
                tuple("previousPeriod", "Previous period", "2017–2021"),
                tuple("producedAt", "Produced at", "2026-10-23 14:05"),
                tuple("producedBy", "Produced by", PRODUCER),
                tuple("partialYear", "Incomplete year", "2026 is not over yet, so its figures are still growing."));
        assertThat(report.note()).contains("agreed to be named", "does not change files already produced");
        assertThat(report.columns()).containsExactly("Key", "Item", "Year", "This period", "Previous period");
    }

    @Test
    void ac3_2_theSectionsFollowTheAnalyticsInOrder() {
        UnitReport report = report(faculty(), ReportLanguage.EN);

        assertThat(report.sections()).extracting(UnitReport.Section::key).containsExactly("totals", "byYear",
            "departments", "byCategory", "byLevel", "bySource", "topPerformers", "highlights");
        assertThat(rows(report, "totals")).extracting(UnitReport.Row::key, UnitReport.Row::current,
            UnitReport.Row::previous).startsWith(
                tuple("approved", BigDecimal.valueOf(50), BigDecimal.valueOf(40)),
                tuple("awardedPeople", BigDecimal.valueOf(30), BigDecimal.valueOf(28)));
        assertThat(rows(report, "totals")).extracting(UnitReport.Row::key).containsExactly("approved",
            "awardedPeople", "personal", "unitAwards", "verified", "members", "perHundredMembers");
        assertThat(rows(report, "byYear")).hasSize(15).first()
            .extracting(UnitReport.Row::key, UnitReport.Row::year, UnitReport.Row::current)
            .containsExactly("total", 2022, BigDecimal.valueOf(10));
    }

    @Test
    void ac3_2_departmentRowsCarryTheComparisonFiguresAndTheYears() {
        List<UnitReport.Row> rows = rows(report(faculty(), ReportLanguage.EN), "departments");

        assertThat(rows).filteredOn(row -> row.key().startsWith("D1."))
            .extracting(UnitReport.Row::key, UnitReport.Row::label, UnitReport.Row::year, UnitReport.Row::current,
                UnitReport.Row::previous)
            .startsWith(
                tuple("D1.members", "Department 1 (inactive) · Members", null, BigDecimal.valueOf(25), null),
                tuple("D1.approved", "Department 1 (inactive) · Approved awards", null, BigDecimal.valueOf(12),
                    BigDecimal.valueOf(10)))
            .contains(tuple("D1.perHundredMembers", "Department 1 (inactive) · Awards per 100 members", null,
                new BigDecimal("48.0"), null),
                tuple("D1.byYear", "Department 1 (inactive) · Approved awards by year", 2026,
                    BigDecimal.valueOf(2), null));
        assertThat(rows).filteredOn(row -> row.key().startsWith("FMI.")).first().extracting(UnitReport.Row::label)
            .isEqualTo("Faculty level · Members");
    }

    @Test
    void ac3_2_namesFollowTheLanguage() {
        UnitReport uk = report(faculty(), ReportLanguage.UK);

        assertThat(uk.title()).isEqualTo("Аналітика підрозділу: Факультет математики та інформатики");
        assertThat(rows(uk, "byCategory")).first().extracting(UnitReport.Row::label).isEqualTo("Категорія 1");
        assertThat(rows(uk, "byLevel")).extracting(UnitReport.Row::label)
            .containsExactly("Національний", "Міжнародний");
        assertThat(rows(uk, "departments")).extracting(UnitReport.Row::label)
            .contains("Кафедра 2 · Працівники");
        assertThat(uk.sections()).extracting(UnitReport.Section::title).contains("Порівняння кафедр");
    }

    @Test
    void ac3_2_sourcesKeepTheirNamesAndTheRestIsOneRow() {
        List<UnitReport.Row> rows = rows(report(faculty(), ReportLanguage.EN), "bySource");

        assertThat(rows).extracting(UnitReport.Row::label, UnitReport.Row::current).startsWith(
            tuple(INJECTED, BigDecimal.valueOf(4))).endsWith(
            tuple("All other organisations", BigDecimal.valueOf(7)));
    }

    @Test
    void ac3_2_topPerformersAndHighlightsAreLabelled() {
        UnitReport report = report(faculty(), ReportLanguage.EN);

        assertThat(rows(report, "topPerformers")).first()
            .extracting(UnitReport.Row::key, UnitReport.Row::label, UnitReport.Row::current)
            .containsExactly("1", "Petrenko Ivan1, Department 1, highest level: International",
                BigDecimal.valueOf(6));
        assertThat(report.peopleNamed()).isEqualTo(5);
        assertThat(rows(report, "highlights")).extracting(UnitReport.Row::key, UnitReport.Row::label).containsExactly(
            tuple("GROWTH", "Strength: Department 1: 12 awards, 4 in the previous period"),
            tuple("NO_AWARDS_EVER", "Gap: Department 4: no awards in this period or the previous one"));
    }

    @Test
    void ac3_2_aDepartmentReportExplainsWhyNobodyIsNamed() {
        UnitReport report = report(department(), ReportLanguage.EN);

        assertThat(report.header()).extracting(UnitReport.Field::key).doesNotContain("partialYear");
        assertThat(rows(report, "departments")).isEmpty();
        assertThat(rows(report, "highlights")).isEmpty();
        assertThat(rows(report, "topPerformers")).extracting(UnitReport.Row::key, UnitReport.Row::current)
            .containsExactly(tuple("groupTooSmall", null));
        assertThat(report.peopleNamed()).isZero();
    }

    private static List<UnitReport.Row> rows(UnitReport report, String key) {
        return report.sections().stream().filter(section -> section.key().equals(key)).findFirst().orElseThrow()
            .rows();
    }
}
