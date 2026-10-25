package ua.edu.chnu.awards.analytics.export;

import static ua.edu.chnu.awards.analytics.export.UnitAnalyticsFixture.messages;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import ua.edu.chnu.awards.analytics.dto.AnalyticsTrends;
import ua.edu.chnu.awards.analytics.dto.CategoryComparison;
import ua.edu.chnu.awards.analytics.dto.CategoryTrend;
import ua.edu.chnu.awards.analytics.dto.ComplianceSummary;
import ua.edu.chnu.awards.analytics.dto.Figure;
import ua.edu.chnu.awards.analytics.dto.LevelComparison;
import ua.edu.chnu.awards.analytics.dto.SourceSummary;
import ua.edu.chnu.awards.analytics.dto.SourceTrend;
import ua.edu.chnu.awards.analytics.dto.TrendProjection;
import ua.edu.chnu.awards.analytics.dto.UnitComparisonRow;
import ua.edu.chnu.awards.analytics.dto.UnitHighlight;
import ua.edu.chnu.awards.analytics.dto.UnitTotals;
import ua.edu.chnu.awards.analytics.dto.UnitYearCount;
import ua.edu.chnu.awards.analytics.dto.UniversityAnalytics;
import ua.edu.chnu.awards.award.dto.CategoryRef;
import ua.edu.chnu.awards.award.dto.YearRange;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.organization.dto.OrganizationRef;
import ua.edu.chnu.awards.organization.entity.OrganizationType;

/**
 * University analytics and a compliance summary shaped like the ones of the seed.
 */
final class UniversityReportFixture {

    static final long UNIVERSITY_ID = 1L;
    static final YearRange PERIOD = new YearRange(2022, 2026);
    static final ZonedDateTime PRODUCED_AT = ZonedDateTime.of(2026, 10, 25, 15, 30, 0, 0, ZoneId.of("Europe/Kyiv"));
    static final String PRODUCER = "Ivan Rector";
    static final String LONG_NAME = "Educational and Scientific Institute of Physical, Technical and Computer "
        + "Sciences with a Deliberately Long Name";

    private static final OrganizationRef UNIVERSITY = new OrganizationRef(UNIVERSITY_ID,
        "Yuriy Fedkovych Chernivtsi National University",
        "Чернівецький національний університет імені Юрія Федьковича", "CHNU", OrganizationType.UNIVERSITY);

    private UniversityReportFixture() {
    }

    static UniversityAnalytics university() {
        return university(8, new TrendProjection(TrendProjection.Status.PROJECTED, new YearRange(2022, 2025),
            new BigDecimal("2.5"), 2027, 31L));
    }

    static UniversityAnalytics university(int faculties, TrendProjection projection) {
        List<OrganizationRef> units = IntStream.rangeClosed(1, faculties)
            .mapToObj(UniversityReportFixture::facultyRef).toList();
        List<UnitComparisonRow> rows = new ArrayList<>();
        rows.add(new UnitComparisonRow(UNIVERSITY, true, true, 3, Figure.of(1, 0), 0, null, 0, years(0)));
        for (int i = 0; i < units.size(); i++) {
            rows.add(new UnitComparisonRow(units.get(i), false, i != 1, 20 + i, Figure.of(10 + i, 8), 6,
                BigDecimal.valueOf(40 + i * 10L, 0).setScale(1), 7, years(2)));
        }
        List<CategoryComparison> byCategory = IntStream.rangeClosed(1, 6)
            .mapToObj(i -> new CategoryComparison(category(i), 12 - i, 5)).toList();
        AnalyticsTrends trends = new AnalyticsTrends(
            new AnalyticsTrends.Directions<>(List.of(CategoryTrend.of(category(1), Figure.of(11, 5))), List.of()),
            new AnalyticsTrends.Directions<>(List.of(), List.of(SourceTrend.of("Ministry of Education",
                Figure.of(2, 9)))));
        return new UniversityAnalytics(UNIVERSITY, PERIOD, new YearRange(2017, 2021), true,
            Instant.parse("2026-10-25T12:00:00Z"),
            new UnitTotals(Figure.of(120, 100), Figure.of(70, 64), 104, 16, 90, 220, new BigDecimal("54.5")),
            IntStream.rangeClosed(PERIOD.from(), PERIOD.to())
                .mapToObj(y -> new UnitYearCount(y, y - 2000L, y - 2002L, 2)).toList(),
            rows, byCategory,
            List.of(new LevelComparison(RecognitionLevel.NATIONAL, 70, 60),
                new LevelComparison(RecognitionLevel.INTERNATIONAL, 50, 40)),
            new SourceSummary(List.of(new SourceSummary.Source("Ministry of Education", 9)), 4), trends, projection,
            List.of(new UnitHighlight(UnitHighlight.Type.ABOVE_AVERAGE, units.getLast(), 17, 8),
                new UnitHighlight(UnitHighlight.Type.BELOW_AVERAGE, units.getFirst(), 10, 8)));
    }

    static UniversityAnalytics empty() {
        return new UniversityAnalytics(UNIVERSITY, new YearRange(2025, 2026), new YearRange(2023, 2024), true,
            Instant.parse("2026-10-25T12:00:00Z"),
            new UnitTotals(Figure.of(0, 0), Figure.of(0, 0), 0, 0, 0, 0, null),
            List.of(new UnitYearCount(2025, 0, 0, 0), new UnitYearCount(2026, 0, 0, 0)), List.of(), List.of(),
            List.of(), new SourceSummary(List.of(), 0),
            new AnalyticsTrends(new AnalyticsTrends.Directions<>(List.of(), List.of()),
                new AnalyticsTrends.Directions<>(List.of(), List.of())),
            TrendProjection.TOO_FEW_YEARS, List.of());
    }

    static BoardReport report(UniversityAnalytics analytics, ComplianceSummary compliance, ReportLanguage language) {
        return new BoardReportAssembler(messages()).assemble(analytics, compliance, language, PRODUCED_AT, PRODUCER);
    }

    private static OrganizationRef facultyRef(int i) {
        String name = i == 2 ? LONG_NAME : "Faculty " + i;
        return new OrganizationRef(10L + i, name, "Факультет " + i, "F" + i, OrganizationType.FACULTY);
    }

    private static CategoryRef category(int i) {
        return new CategoryRef((long) i, "Category " + i, "Категорія " + i, RecognitionLevel.NATIONAL);
    }

    private static List<Long> years(long count) {
        return IntStream.rangeClosed(PERIOD.from(), PERIOD.to()).mapToObj(y -> count).toList();
    }
}
