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
import static ua.edu.chnu.awards.analytics.service.UnitTreeFixture.ARCHIVE;
import static ua.edu.chnu.awards.analytics.service.UnitTreeFixture.COLLEGE;
import static ua.edu.chnu.awards.analytics.service.UnitTreeFixture.DAI;
import static ua.edu.chnu.awards.analytics.service.UnitTreeFixture.FMI;
import static ua.edu.chnu.awards.analytics.service.UnitTreeFixture.HISTORY;
import static ua.edu.chnu.awards.analytics.service.UnitTreeFixture.UNIVERSITY;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.analytics.config.AnalyticsProperties;
import ua.edu.chnu.awards.analytics.dto.TrendProjection;
import ua.edu.chnu.awards.analytics.dto.UnitComparisonRow;
import ua.edu.chnu.awards.analytics.dto.UnitFigures;
import ua.edu.chnu.awards.analytics.dto.UnitHighlight;
import ua.edu.chnu.awards.analytics.dto.UniversityAnalytics;
import ua.edu.chnu.awards.award.dto.YearRange;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.organization.OrganizationTree;
import ua.edu.chnu.awards.organization.entity.OrganizationType;
import ua.edu.chnu.awards.organization.service.OrganizationLookup;

@ExtendWith(MockitoExtension.class)
class UniversityAnalyticsServiceTest {

    private static final int MIN_GROUP_SIZE = 5;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneId.of("Europe/Kyiv"));

    @Mock
    private OrganizationTree tree;

    @Mock
    private OrganizationLookup lookup;

    @Mock
    private UnitAggregates aggregates;

    private UniversityAnalyticsService service;

    @BeforeEach
    void setUp() {
        new UnitTreeFixture().stub(tree, lookup);
        AnalyticsProperties properties = new AnalyticsProperties(MIN_GROUP_SIZE, 500, 10, Duration.ofMinutes(5),
            Duration.ofDays(2));
        service = new UniversityAnalyticsService(tree, lookup, aggregates, new UnitComparison(tree, lookup),
            new HighlightRules(properties), new TrendRules(), CLOCK);
    }

    @Test
    void ac1_2_theUniversityIsReadOverTheDefaultPeriod() {
        when(aggregates.of(anyLong(), any(), any())).thenReturn(empty());

        UniversityAnalytics analytics = service.of(null, null);

        assertThat(analytics.unit().id()).isEqualTo(UNIVERSITY);
        assertThat(analytics.unit().type()).isEqualTo(OrganizationType.UNIVERSITY);
        assertThat(analytics.period()).isEqualTo(PERIOD);
        assertThat(analytics.previousPeriod()).isEqualTo(PREVIOUS);
        assertThat(analytics.partialYear()).isTrue();
        assertThat(analytics.generatedAt()).isEqualTo(GENERATED);
        verify(aggregates).of(UNIVERSITY, PREVIOUS, PERIOD);
    }

    @Test
    void ac1_2_invalidYearsNameTheParameter() {
        assertInvalid(2027, null, "from");
        assertInvalid(2026, 2021, "from");
        assertInvalid(null, 2101, "to");
    }

    @Test
    void ac1_2_withoutAUniversityTheReadFails() {
        when(tree.university()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.of(null, null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ac1_3_ac1_4_facultiesAndCollegesByUkrainianNameThenTheUniversityLevelAddUpToTheTotal() {
        UnitFigures figures = figures(
            List.of(count(DAI, 2024, MINISTRY, false, 3, 1), count(FMI, 2025, GRANT, true, 1, 0),
                count(ARCHIVE, 2023, GRANT, false, 2, 0), count(COLLEGE, 2022, GRANT, false, 1, 0),
                count(UNIVERSITY, 2026, MINISTRY, true, 1, 0), count(DAI, 2019, MINISTRY, false, 4, 0)),
            List.of(), List.of(), List.of(members(DAI, 6), members(ARCHIVE, 5), members(UNIVERSITY, 1)));
        when(aggregates.of(UNIVERSITY, PREVIOUS, PERIOD)).thenReturn(figures);

        UniversityAnalytics analytics = service.of(null, null);

        assertThat(analytics.units()).extracting(row -> row.unit().id(), UnitComparisonRow::facultyLevel,
                UnitComparisonRow::members, row -> row.approved().current(), row -> row.approved().previous())
            .containsExactly(tuple(HISTORY, false, 5L, 2L, 0L), tuple(COLLEGE, false, 0L, 1L, 0L),
                tuple(FMI, false, 6L, 4L, 4L), tuple(UNIVERSITY, true, 1L, 1L, 0L));
        assertThat(analytics.units().stream().mapToLong(row -> row.approved().current()).sum())
            .isEqualTo(analytics.totals().approved().current()).isEqualTo(8);
        assertThat(analytics.byYear()).hasSize(5);
        assertThat(analytics.byCategory()).isNotEmpty();
    }

    @Test
    void ac1_5_ac1_6_ac1_7_trendsProjectionAndHighlightsComeWithTheAnalytics() {
        UnitFigures figures = figures(
            List.of(count(DAI, 2022, MINISTRY, false, 10, 0), count(DAI, 2023, MINISTRY, false, 12, 0),
                count(DAI, 2024, MINISTRY, false, 14, 0), count(DAI, 2025, MINISTRY, false, 16, 0),
                count(ARCHIVE, 2019, GRANT, false, 6, 0)),
            List.of(), List.of(), List.of(members(DAI, 10), members(ARCHIVE, 10), members(UNIVERSITY, 1)));
        when(aggregates.of(UNIVERSITY, PREVIOUS, PERIOD)).thenReturn(figures);

        UniversityAnalytics analytics = service.of(null, null);

        assertThat(analytics.trends().categories().rising()).extracting(trend -> trend.category().id())
            .containsExactly(MINISTRY.id());
        assertThat(analytics.trends().categories().falling()).extracting(trend -> trend.category().id())
            .containsExactly(GRANT.id());
        assertThat(analytics.projection().status()).isEqualTo(TrendProjection.Status.PROJECTED);
        assertThat(analytics.projection().basis()).isEqualTo(new YearRange(2022, 2025));
        assertThat(analytics.projection().estimate()).isEqualTo(18L);
        assertThat(analytics.highlights()).extracting(UnitHighlight::type, highlight -> highlight.unit().id())
            .containsExactly(tuple(UnitHighlight.Type.NO_AWARDS, HISTORY),
                tuple(UnitHighlight.Type.ABOVE_AVERAGE, FMI));
    }

    private void assertInvalid(Integer from, Integer to, String parameter) {
        assertThatThrownBy(() -> service.of(from, to)).isInstanceOfSatisfying(ApiProblemException.class,
            problem -> {
                assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(problem.getType()).isEqualTo("invalid-parameter");
                assertThat(problem.getProperties()).containsEntry("parameter", parameter);
            });
    }
}
