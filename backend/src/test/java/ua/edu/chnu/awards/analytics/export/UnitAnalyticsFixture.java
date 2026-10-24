package ua.edu.chnu.awards.analytics.export;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import ua.edu.chnu.awards.analytics.dto.AnalysableUnit;
import ua.edu.chnu.awards.analytics.dto.CategoryComparison;
import ua.edu.chnu.awards.analytics.dto.Figure;
import ua.edu.chnu.awards.analytics.dto.LevelComparison;
import ua.edu.chnu.awards.analytics.dto.SourceSummary;
import ua.edu.chnu.awards.analytics.dto.TopPerformers;
import ua.edu.chnu.awards.analytics.dto.UnitAnalytics;
import ua.edu.chnu.awards.analytics.dto.UnitComparisonRow;
import ua.edu.chnu.awards.analytics.dto.UnitHighlight;
import ua.edu.chnu.awards.analytics.dto.UnitTotals;
import ua.edu.chnu.awards.analytics.dto.UnitYearCount;
import ua.edu.chnu.awards.award.dto.CategoryRef;
import ua.edu.chnu.awards.award.dto.YearRange;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.organization.dto.OrganizationRef;
import ua.edu.chnu.awards.organization.entity.OrganizationType;

/**
 * Unit analytics shaped like the ones of a seeded faculty.
 */
final class UnitAnalyticsFixture {

    static final long FACULTY_ID = 9L;
    static final YearRange PERIOD = new YearRange(2022, 2026);
    static final YearRange PREVIOUS = new YearRange(2017, 2021);
    static final ZonedDateTime PRODUCED_AT = ZonedDateTime.of(2026, 10, 23, 14, 5, 0, 0, ZoneId.of("Europe/Kyiv"));
    static final String PRODUCER = "Olena Dean";
    static final String INJECTED = "=HYPERLINK(\"http://example.org\")";

    private static final AnalysableUnit FMI = new AnalysableUnit(FACULTY_ID, "Faculty of Mathematics and Informatics",
        "Факультет математики та інформатики", "FMI", OrganizationType.FACULTY, 1L);
    private static final OrganizationRef FMI_REF = new OrganizationRef(FACULTY_ID, FMI.name(), FMI.nameUk(), "FMI",
        OrganizationType.FACULTY);

    private UnitAnalyticsFixture() {
    }

    static UnitAnalytics faculty() {
        return faculty(4, 6, 5, 5);
    }

    static UnitAnalytics faculty(int departments, int categories, int sources, int people) {
        List<OrganizationRef> units = IntStream.rangeClosed(1, departments)
            .mapToObj(UnitAnalyticsFixture::departmentRef).toList();
        List<UnitComparisonRow> rows = new ArrayList<>();
        rows.add(new UnitComparisonRow(FMI_REF, true, true, 0, Figure.of(2, 1), 2, null, 1, years(1)));
        units.forEach(unit -> rows.add(new UnitComparisonRow(unit, false, unit.id() != 101L, 25,
            Figure.of(12, 10), 8, new BigDecimal("48.0"), 9, years(2))));
        List<CategoryComparison> byCategory = IntStream.rangeClosed(1, categories)
            .mapToObj(i -> new CategoryComparison(new CategoryRef((long) i, "Category " + i, "Категорія " + i,
                RecognitionLevel.NATIONAL), 10 - i, 5)).toList();
        List<SourceSummary.Source> sourceRows = new ArrayList<>();
        sourceRows.add(new SourceSummary.Source(INJECTED, 4));
        IntStream.rangeClosed(2, sources).forEach(i -> sourceRows.add(new SourceSummary.Source("Ministry " + i, 3)));
        List<TopPerformers.Person> listed = IntStream.rangeClosed(1, people)
            .mapToObj(i -> new TopPerformers.Person(200L + i, "Ivan" + i, "Petrenko", units.getFirst(), 6 - i / 2,
                RecognitionLevel.INTERNATIONAL)).toList();
        return new UnitAnalytics(FMI, PERIOD, PREVIOUS, true, Instant.parse("2026-10-23T11:00:00Z"),
            new UnitTotals(Figure.of(50, 40), Figure.of(30, 28), 44, 6, 41, 100, new BigDecimal("50.0")),
            IntStream.rangeClosed(PERIOD.from(), PERIOD.to()).mapToObj(y -> new UnitYearCount(y, 10, 9, 1)).toList(),
            rows, byCategory,
            List.of(new LevelComparison(RecognitionLevel.NATIONAL, 30, 20),
                new LevelComparison(RecognitionLevel.INTERNATIONAL, 20, 20)),
            new SourceSummary(sourceRows, 7), new TopPerformers(TopPerformers.Status.LISTED, listed),
            List.of(new UnitHighlight(UnitHighlight.Type.GROWTH, units.getFirst(), 12, 4),
                new UnitHighlight(UnitHighlight.Type.NO_AWARDS, units.getLast(), 0, 0)));
    }

    static UnitAnalytics department() {
        OrganizationRef dai = departmentRef(1);
        AnalysableUnit unit = new AnalysableUnit(dai.id(), dai.name(), dai.nameUk(), dai.code(),
            OrganizationType.DEPARTMENT, FACULTY_ID);
        return new UnitAnalytics(unit, PERIOD, PREVIOUS, false, Instant.parse("2026-10-23T11:00:00Z"),
            new UnitTotals(Figure.of(3, 0), Figure.of(2, 0), 3, 0, 1, 4, new BigDecimal("75.0")),
            List.of(new UnitYearCount(2026, 3, 3, 0)), List.of(), List.of(), List.of(),
            new SourceSummary(List.of(), 0), new TopPerformers(TopPerformers.Status.GROUP_TOO_SMALL, List.of()),
            List.of());
    }

    static MessageSource messages() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messages.setFallbackToSystemLocale(false);
        return messages;
    }

    static UnitReport report(UnitAnalytics analytics, ReportLanguage language) {
        return new UnitReportAssembler(messages()).assemble(analytics, language, PRODUCED_AT, PRODUCER);
    }

    private static OrganizationRef departmentRef(int i) {
        return new OrganizationRef(100L + i, "Department " + i, "Кафедра " + i, "D" + i, OrganizationType.DEPARTMENT);
    }

    private static List<Long> years(long count) {
        return IntStream.rangeClosed(PERIOD.from(), PERIOD.to()).mapToObj(y -> count).toList();
    }
}
