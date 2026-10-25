package ua.edu.chnu.awards.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.DIPLOMA;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.GENERATED;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.GRANT;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.MINISTRY;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.PERIOD;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.PREVIOUS;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.count;
import static ua.edu.chnu.awards.analytics.service.UnitFiguresFixture.spelling;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.analytics.dto.AnalyticsTrends;
import ua.edu.chnu.awards.analytics.dto.CategoryTrend;
import ua.edu.chnu.awards.analytics.dto.SourceTrend;
import ua.edu.chnu.awards.analytics.dto.UnitFigures;
import ua.edu.chnu.awards.award.dto.CategoryRef;
import ua.edu.chnu.awards.award.dto.SourceSpelling;
import ua.edu.chnu.awards.award.dto.UnitAwardCount;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;

class TrendRulesTest {

    private final TrendRules rules = new TrendRules();

    @Test
    void ac1_5_categoriesRiseAndFallByDeltaWithPercentAndNullForNewOnes() {
        UnitFigures figures = figures(List.of(count(9, 2024, MINISTRY, false, 6, 0),
            count(9, 2019, MINISTRY, false, 2, 0), count(9, 2023, GRANT, false, 3, 0),
            count(9, 2018, DIPLOMA, false, 5, 0), count(9, 2025, DIPLOMA, false, 1, 0),
            count(9, 2015, GRANT, false, 40, 0)), List.of(), List.of());

        AnalyticsTrends trends = rules.of(figures, PERIOD, PREVIOUS);

        assertThat(trends.categories().rising())
            .extracting(trend -> trend.category().id(), CategoryTrend::current, CategoryTrend::previous,
                CategoryTrend::delta, CategoryTrend::deltaPercent)
            .containsExactly(tuple(13L, 6L, 2L, 4L, new BigDecimal("200.0")), tuple(20L, 3L, 0L, 3L, null));
        assertThat(trends.categories().falling())
            .extracting(trend -> trend.category().id(), CategoryTrend::delta, CategoryTrend::deltaPercent)
            .containsExactly(tuple(21L, -4L, new BigDecimal("-80.0")));
    }

    @Test
    void ac1_5_entriesUnderThreeAwardsOverBothPeriodsAndUnchangedOnesAreLeftOut() {
        UnitFigures figures = figures(List.of(count(9, 2024, MINISTRY, false, 2, 0),
            count(9, 2024, GRANT, false, 2, 0), count(9, 2019, GRANT, false, 2, 0)), List.of(), List.of());

        AnalyticsTrends trends = rules.of(figures, PERIOD, PREVIOUS);

        assertThat(trends.categories().rising()).isEmpty();
        assertThat(trends.categories().falling()).isEmpty();
    }

    @Test
    void ac1_5_atMostFivePerDirectionTiesByName() {
        List<UnitAwardCount> counts = new ArrayList<>();
        IntStream.rangeClosed(1, 7).forEach(id -> counts.add(count(9, 2024, category(id), false, 3, 0)));

        AnalyticsTrends trends = rules.of(figures(counts, List.of(), List.of()), PERIOD, PREVIOUS);

        assertThat(trends.categories().rising()).extracting(trend -> trend.category().name())
            .containsExactly("Category 1", "Category 2", "Category 3", "Category 4", "Category 5");
    }

    @Test
    void ac1_5_sourcesAreGroupedByKeyOverBothPeriodsUnderTheMostFrequentSpelling() {
        UnitFigures figures = figures(List.of(),
            List.of(spelling("ministry", "Ministry of Education", 2), spelling("ministry", "MoE", 3),
                spelling("nas", "NAS", 1)),
            List.of(spelling("ministry", "Ministry of Education", 2), spelling("nas", "NAS", 4),
                spelling("nas", "NAS of Ukraine", 1)));

        AnalyticsTrends trends = rules.of(figures, PERIOD, PREVIOUS);

        assertThat(trends.sources().rising()).extracting(SourceTrend::name, SourceTrend::current,
                SourceTrend::previous, SourceTrend::deltaPercent)
            .containsExactly(tuple("Ministry of Education", 5L, 2L, new BigDecimal("150.0")));
        assertThat(trends.sources().falling()).extracting(SourceTrend::name, SourceTrend::delta)
            .containsExactly(tuple("NAS", -4L));
    }

    private static UnitFigures figures(List<UnitAwardCount> counts, List<SourceSpelling> sources,
                                       List<SourceSpelling> previousSources) {
        return new UnitFigures(GENERATED, counts, List.of(), sources, previousSources, List.of());
    }

    private static CategoryRef category(long id) {
        return new CategoryRef(id, "Category " + id, "Категорія " + id, RecognitionLevel.FACULTY);
    }
}
