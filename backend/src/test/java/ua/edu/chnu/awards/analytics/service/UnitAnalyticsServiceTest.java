package ua.edu.chnu.awards.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.GENERATED;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.GRANT;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.MINISTRY;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.PERIOD;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.PREVIOUS;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.count;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.empty;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.figures;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.members;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.owner;
import static ua.edu.chnu.awards.analytics.service.UnitTreeFixture.CLOSED_WITH_AWARDS;
import static ua.edu.chnu.awards.analytics.service.UnitTreeFixture.DAI;
import static ua.edu.chnu.awards.analytics.service.UnitTreeFixture.DMA;
import static ua.edu.chnu.awards.analytics.service.UnitTreeFixture.FMI;
import static ua.edu.chnu.awards.analytics.service.UnitTreeFixture.SPECIALITY;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.analytics.config.AnalyticsProperties;
import ua.edu.chnu.awards.analytics.dto.AnalysableUnit;
import ua.edu.chnu.awards.analytics.dto.TopPerformers;
import ua.edu.chnu.awards.analytics.dto.UnitAnalytics;
import ua.edu.chnu.awards.analytics.dto.UnitComparisonRow;
import ua.edu.chnu.awards.analytics.dto.UnitFigures;
import ua.edu.chnu.awards.analytics.dto.UnitHighlight;
import ua.edu.chnu.awards.award.dto.YearRange;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.organization.OrganizationTree;
import ua.edu.chnu.awards.organization.entity.OrganizationType;
import ua.edu.chnu.awards.organization.service.OrganizationLookup;

@ExtendWith(MockitoExtension.class)
class UnitAnalyticsServiceTest {

    private static final int MIN_GROUP_SIZE = 5;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneId.of("Europe/Kyiv"));
    private static final AnalysableUnit FACULTY = new AnalysableUnit(FMI, "Faculty of Mathematics",
        "Факультет математики", "C9", OrganizationType.FACULTY, 1L);
    private static final AnalysableUnit DEPARTMENT = new AnalysableUnit(DAI, "Algebra and Informatics",
        "Кафедра алгебри та інформатики", "C64", OrganizationType.DEPARTMENT, FMI);
    private static final TopPerformers NOBODY = new TopPerformers(TopPerformers.Status.GROUP_TOO_SMALL, List.of());

    @Mock
    private UnitScope scope;
    @Mock
    private UnitAggregates aggregates;
    @Mock
    private TopPerformersRanking ranking;
    @Mock
    private OrganizationTree tree;
    @Mock
    private OrganizationLookup lookup;

    private UnitAnalyticsService service;

    @BeforeEach
    void setUp() {
        new UnitTreeFixture().stub(tree, lookup);
        AnalyticsProperties properties = new AnalyticsProperties(MIN_GROUP_SIZE, 500, 10, Duration.ofMinutes(5),
            Duration.ofDays(2));
        service = new UnitAnalyticsService(scope, aggregates, new UnitComparison(tree, lookup),
            new HighlightRules(properties), ranking, CLOCK);
    }

    @Test
    void ac1_3_thePeriodDefaultsToFiveYearsEndingNowComparedWithTheFiveBefore() {
        UnitAnalytics analytics = read(FACULTY, empty());

        assertThat(analytics.period()).isEqualTo(PERIOD);
        assertThat(analytics.previousPeriod()).isEqualTo(PREVIOUS);
        assertThat(analytics.partialYear()).isTrue();
        assertThat(analytics.generatedAt()).isEqualTo(GENERATED);
        assertThat(analytics.unit()).isEqualTo(FACULTY);
        verify(aggregates).of(FMI, PREVIOUS, PERIOD);
    }

    @Test
    void ac1_3_aSingleYearIsComparedWithTheYearBeforeAndAPastYearIsComplete() {
        when(scope.require(DAI)).thenReturn(DEPARTMENT);
        when(aggregates.of(anyLong(), any(), any())).thenReturn(empty());

        UnitAnalytics analytics = service.of(DAI, 2024, 2024);

        assertThat(analytics.previousPeriod()).isEqualTo(new YearRange(2023, 2023));
        assertThat(analytics.partialYear()).isFalse();
        assertThat(service.of(DAI, 2026, null).period()).isEqualTo(new YearRange(2026, 2026));
        verify(aggregates).of(DAI, new YearRange(2023, 2023), new YearRange(2024, 2024));
    }

    @Test
    void ac1_3_invalidYearsNameTheParameterAndUnknownUnitsAreNotFound() {
        when(scope.require(anyLong())).thenReturn(FACULTY);
        assertInvalid(2027, null, "from");
        assertInvalid(2026, 2021, "from");
        assertInvalid(1949, null, "from");
        assertInvalid(null, 2101, "to");
        when(scope.require(DMA)).thenThrow(new UnitNotFoundException(DMA));
        assertThatThrownBy(() -> service.of(DMA, null, null)).isInstanceOf(UnitNotFoundException.class);
    }

    @Test
    void ac1_5_theFacultyRowsCoverEveryActiveDepartmentAndTheFacultyLevelAndAddUp() {
        UnitFigures figures = figures(
            List.of(count(DAI, 2024, MINISTRY, false, 3, 1), count(SPECIALITY, 2022, GRANT, false, 1, 0),
                count(FMI, 2026, GRANT, true, 2, 0), count(CLOSED_WITH_AWARDS, 2023, GRANT, false, 1, 0),
                count(DAI, 2019, MINISTRY, false, 2, 0)),
            List.of(owner(1, DAI, true, 3), owner(3, SPECIALITY, true, 1), owner(4, CLOSED_WITH_AWARDS, true, 1)),
            List.of(), List.of(members(DAI, 4), members(SPECIALITY, 1), members(DMA, 2)));

        List<UnitComparisonRow> rows = read(FACULTY, figures).units();

        assertThat(rows).extracting(row -> row.unit().id(), UnitComparisonRow::facultyLevel, UnitComparisonRow::active,
                UnitComparisonRow::members, row -> row.approved().current(), row -> row.approved().previous(),
                UnitComparisonRow::awardedPeople, UnitComparisonRow::byYear)
            .containsExactly(
                tuple(DAI, false, true, 5L, 4L, 2L, 2L, List.of(1L, 0L, 3L, 0L, 0L)),
                tuple(CLOSED_WITH_AWARDS, false, false, 0L, 1L, 0L, 1L, List.of(0L, 1L, 0L, 0L, 0L)),
                tuple(DMA, false, true, 2L, 0L, 0L, 0L, List.of(0L, 0L, 0L, 0L, 0L)),
                tuple(FMI, true, true, 0L, 2L, 0L, 0L, List.of(0L, 0L, 0L, 0L, 2L)));
        assertThat(rows.stream().mapToLong(row -> row.approved().current()).sum()).isEqualTo(7);
        assertThat(rows.getFirst().perHundredMembers()).isEqualByComparingTo("80.0");
        assertThat(rows.getFirst().verified()).isEqualTo(1);
        assertThat(rows.get(2).perHundredMembers()).isEqualByComparingTo("0.0");
    }

    @Test
    void ac1_5_aDepartmentHasNoRowsAndNoHighlights() {
        UnitAnalytics analytics = read(DEPARTMENT, figures(List.of(count(DAI, 2024, MINISTRY, false, 3, 1)),
            List.of(), List.of(), List.of(members(DAI, 4))));

        assertThat(analytics.units()).isEmpty();
        assertThat(analytics.highlights()).isEmpty();
        assertThat(analytics.totals().approved().current()).isEqualTo(3);
    }

    @Test
    void ac1_7_topPerformersAreAskedForWithTheUnitMembers() {
        UnitFigures figures = figures(List.of(), List.of(), List.of(), List.of(members(DAI, 7)));
        TopPerformers listed = new TopPerformers(TopPerformers.Status.LISTED, List.of());
        when(ranking.of(figures, 7)).thenReturn(listed);
        when(scope.require(DAI)).thenReturn(DEPARTMENT);
        when(aggregates.of(DAI, PREVIOUS, PERIOD)).thenReturn(figures);

        assertThat(service.of(DAI, null, null).topPerformers()).isSameAs(listed);
    }

    @Test
    void ac1_8_theFacultyHighlightsComeFromItsDepartmentRows() {
        UnitFigures figures = figures(List.of(count(DAI, 2024, MINISTRY, false, 8, 0),
            count(DAI, 2019, MINISTRY, false, 4, 0)), List.of(), List.of(), List.of(members(DAI, 4), members(DMA, 2)));

        List<UnitHighlight> highlights = read(FACULTY, figures).highlights();

        assertThat(highlights).extracting(UnitHighlight::type, highlight -> highlight.unit().id())
            .containsExactly(tuple(UnitHighlight.Type.NO_AWARDS, DMA), tuple(UnitHighlight.Type.GROWTH, DAI));
    }

    @Test
    void ac1_2_unitsComeFromTheScope() {
        when(scope.list()).thenReturn(List.of(FACULTY, DEPARTMENT));

        assertThat(service.units()).containsExactly(FACULTY, DEPARTMENT);
    }

    private UnitAnalytics read(AnalysableUnit unit, UnitFigures figures) {
        when(scope.require(unit.id())).thenReturn(unit);
        when(aggregates.of(anyLong(), any(), any())).thenReturn(figures);
        when(ranking.of(any(), anyLong())).thenReturn(NOBODY);
        return service.of(unit.id(), null, null);
    }

    private void assertInvalid(Integer from, Integer to, String parameter) {
        assertThatThrownBy(() -> service.of(FMI, from, to)).isInstanceOfSatisfying(ApiProblemException.class,
            problem -> {
                assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(problem.getType()).isEqualTo("invalid-parameter");
                assertThat(problem.getProperties()).containsEntry("parameter", parameter);
            });
    }
}
