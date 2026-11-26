package ua.edu.chnu.awards.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.analytics.dto.TrendProjection;
import ua.edu.chnu.awards.analytics.dto.UnitYearCount;
import ua.edu.chnu.awards.award.dto.YearRange;

class ProjectionHelperTest {

    private static final int CURRENT_YEAR = 2026;

    @Test
    void ac1_6_aLineThroughTheCompleteYearsLeavesThePartialYearOut() {
        TrendProjection projection = ProjectionHelper.of(years(2022, 10, 12, 14, 16, 1), CURRENT_YEAR);

        assertThat(projection.status()).isEqualTo(TrendProjection.Status.PROJECTED);
        assertThat(projection.basis()).isEqualTo(new YearRange(2022, 2025));
        assertThat(projection.slope()).isEqualByComparingTo(new BigDecimal("2.0"));
        assertThat(projection.year()).isEqualTo(CURRENT_YEAR);
        assertThat(projection.estimate()).isEqualTo(18L);
    }

    @Test
    void ac1_6_theSlopeHasOneDecimalAndTheEstimateIsRounded() {
        TrendProjection projection = ProjectionHelper.of(years(2021, 5, 6, 8), 2024);

        assertThat(projection.slope()).isEqualTo(new BigDecimal("1.5"));
        assertThat(projection.estimate()).isEqualTo(9L);
    }

    @Test
    void ac1_6_aFallingLineIsClampedAtZero() {
        TrendProjection projection = ProjectionHelper.of(years(2023, 20, 10, 1), CURRENT_YEAR);

        assertThat(projection.slope()).isEqualTo(new BigDecimal("-9.5"));
        assertThat(projection.estimate()).isZero();
    }

    @Test
    void ac1_6_fewerThanThreeCompleteYearsGiveNoLine() {
        assertThat(ProjectionHelper.of(years(2024, 3, 4, 5), CURRENT_YEAR)).isEqualTo(TrendProjection.TOO_FEW_YEARS);
        assertThat(ProjectionHelper.of(years(2026, 5), CURRENT_YEAR).basis()).isNull();
    }

    private static List<UnitYearCount> years(int from, long... totals) {
        return IntStream.range(0, totals.length)
            .mapToObj(index -> new UnitYearCount(from + index, totals[index], totals[index], 0)).toList();
    }
}
