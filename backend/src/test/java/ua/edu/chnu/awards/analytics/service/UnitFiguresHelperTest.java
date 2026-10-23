package ua.edu.chnu.awards.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.DIPLOMA;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.GRANT;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.MINISTRY;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.PERIOD;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.PREVIOUS;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.count;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.figures;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.members;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.owner;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.spelling;
import static ua.edu.chnu.awards.analytics.service.UnitTreeFixture.DAI;
import static ua.edu.chnu.awards.analytics.service.UnitTreeFixture.DMA;
import static ua.edu.chnu.awards.analytics.service.UnitTreeFixture.FMI;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.analytics.dto.CategoryComparison;
import ua.edu.chnu.awards.analytics.dto.Figure;
import ua.edu.chnu.awards.analytics.dto.LevelComparison;
import ua.edu.chnu.awards.analytics.dto.SourceSummary;
import ua.edu.chnu.awards.analytics.dto.UnitFigures;
import ua.edu.chnu.awards.analytics.dto.UnitTotals;
import ua.edu.chnu.awards.analytics.dto.UnitYearCount;
import ua.edu.chnu.awards.award.dto.SourceSpelling;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;

class UnitFiguresHelperTest {

    private static final int SOURCE_LIMIT = 15;

    @Test
    void ac1_4_totalsCompareTheCountedAwardsAndAwardedPeopleWithThePreviousPeriod() {
        UnitFigures figures = figures(
            List.of(count(DAI, 2024, MINISTRY, false, 3, 1), count(DAI, 2025, GRANT, true, 1, 0),
                count(FMI, 2026, GRANT, true, 2, 0), count(DAI, 2019, MINISTRY, false, 2, 2)),
            List.of(owner(1, DAI, true, 2), owner(2, DAI, true, 1), owner(1, DAI, false, 2)),
            List.of(), List.of(members(DAI, 4), members(FMI, 1)));

        UnitTotals totals = UnitFiguresHelper.totals(figures, PERIOD, PREVIOUS);

        assertThat(totals.approved()).extracting(Figure::current, Figure::previous, Figure::delta)
            .containsExactly(6L, 2L, 4L);
        assertThat(totals.approved().deltaPercent()).isEqualByComparingTo("200.0");
        assertThat(totals.awardedPeople()).extracting(Figure::current, Figure::previous).containsExactly(2L, 1L);
        assertThat(totals).extracting(UnitTotals::personal, UnitTotals::unitAwards, UnitTotals::verified,
            UnitTotals::members).containsExactly(3L, 3L, 1L, 5L);
        assertThat(totals.perHundredMembers()).isEqualByComparingTo("120.0");
    }

    @Test
    void ac1_4_withoutPreviousAwardsOrMembersTheRatiosAreNull() {
        UnitTotals totals = UnitFiguresHelper.totals(figures(List.of(count(DAI, 2024, MINISTRY, false, 3, 0)),
            List.of(), List.of(), List.of()), PERIOD, PREVIOUS);

        assertThat(totals.approved().deltaPercent()).isNull();
        assertThat(totals.perHundredMembers()).isNull();
        assertThat(Figure.of(2, 3).deltaPercent()).isEqualByComparingTo("-33.3");
    }

    @Test
    void ac1_6_seriesListEveryYearAndCompareCategoriesAndLevelsWithThePreviousPeriod() {
        UnitFigures figures = figures(
            List.of(count(DAI, 2024, MINISTRY, false, 2, 0), count(DAI, 2024, GRANT, true, 1, 0),
                count(DMA, 2025, DIPLOMA, false, 1, 0), count(DAI, 2026, GRANT, false, 1, 0),
                count(DAI, 2019, MINISTRY, false, 4, 0), count(DAI, 2010, MINISTRY, false, 9, 0)),
            List.of(), List.of(), List.of());

        assertThat(UnitFiguresHelper.byYear(figures, PERIOD))
            .extracting(UnitYearCount::year, UnitYearCount::total, UnitYearCount::personal, UnitYearCount::unitAwards)
            .containsExactly(tuple(2022, 0L, 0L, 0L), tuple(2023, 0L, 0L, 0L), tuple(2024, 3L, 2L, 1L),
                tuple(2025, 1L, 1L, 0L), tuple(2026, 1L, 1L, 0L));
        assertThat(UnitFiguresHelper.byCategory(figures, PERIOD, PREVIOUS))
            .extracting(CategoryComparison::category, CategoryComparison::count, CategoryComparison::previous)
            .containsExactly(tuple(MINISTRY, 2L, 4L), tuple(GRANT, 2L, 0L), tuple(DIPLOMA, 1L, 0L));
        assertThat(UnitFiguresHelper.byLevel(figures, PERIOD, PREVIOUS))
            .extracting(LevelComparison::level, LevelComparison::count, LevelComparison::previous)
            .containsExactly(tuple(RecognitionLevel.FACULTY, 3L, 0L), tuple(RecognitionLevel.NATIONAL, 2L, 4L));
        assertThat(UnitFiguresHelper.yearSeries(figures, UnitFiguresHelper.in(Set.of(DMA)), PERIOD))
            .containsExactly(0L, 0L, 0L, 1L, 0L);
    }

    @Test
    void ac1_6_sourcesGroupTheirSpellingsUnderTheMostFrequentAndKeepTheFifteenLargest() {
        List<SourceSpelling> spellings = new ArrayList<>(List.of(spelling("мон україни", "МОН України", 3),
            spelling("мон україни", "мон  України", 2), spelling("мон україни", "Мон україни", 1)));
        IntStream.rangeClosed(1, SOURCE_LIMIT).forEach(index ->
            spellings.add(spelling("body " + index, "Body " + index, 1)));

        SourceSummary sources = UnitFiguresHelper.bySource(figures(List.of(), List.of(), spellings, List.of()));

        assertThat(sources.sources()).hasSize(SOURCE_LIMIT);
        assertThat(sources.sources().getFirst()).extracting(SourceSummary.Source::name, SourceSummary.Source::count)
            .containsExactly("МОН України", 6L);
        assertThat(sources.otherCount()).isEqualTo(1);
    }
}
