package ua.edu.chnu.awards.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.analytics.dto.CategoryCount;
import ua.edu.chnu.awards.analytics.dto.LevelCount;
import ua.edu.chnu.awards.analytics.dto.YearCount;
import ua.edu.chnu.awards.award.dto.CategoryRef;
import ua.edu.chnu.awards.award.dto.CategoryYearCount;
import ua.edu.chnu.awards.award.dto.YearRange;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;

class AwardCountsHelperTest {

    private static final CategoryRef MINISTRY = new CategoryRef(13L, "Ministry Recognition", "Відзнака міністерства",
        RecognitionLevel.NATIONAL);
    private static final CategoryRef STATE_PRIZE = new CategoryRef(12L, "State Prize", "Державна премія",
        RecognitionLevel.NATIONAL);
    private static final CategoryRef BEST_TEACHER = new CategoryRef(22L, "Best Teacher Award",
        "Нагорода найкращого викладача", RecognitionLevel.UNIVERSITY);
    private static final CategoryRef FACULTY_AWARD = new CategoryRef(31L, "Faculty Teaching Award",
        "Факультетська педагогічна нагорода", RecognitionLevel.FACULTY);
    private static final List<CategoryYearCount> COUNTS = List.of(
        new CategoryYearCount(2021, MINISTRY, 1), new CategoryYearCount(2023, STATE_PRIZE, 1),
        new CategoryYearCount(2023, FACULTY_AWARD, 1), new CategoryYearCount(2023, MINISTRY, 1),
        new CategoryYearCount(2026, STATE_PRIZE, 1), new CategoryYearCount(2026, BEST_TEACHER, 1));

    @Test
    void ac1_3_everyYearOfThePeriodIsListedWithItsLevels() {
        List<YearCount> years = AwardCountsHelper.byYear(new YearRange(2021, 2026), COUNTS);

        assertThat(years).extracting(YearCount::year).containsExactly(2021, 2022, 2023, 2024, 2025, 2026);
        assertThat(years).extracting(YearCount::total).containsExactly(1L, 0L, 3L, 0L, 0L, 2L);
        assertThat(years.get(2).byLevel()).containsExactly(
            Map.entry(RecognitionLevel.FACULTY, 1L), Map.entry(RecognitionLevel.NATIONAL, 2L));
        assertThat(years.get(1).byLevel()).isEmpty();
    }

    @Test
    void ac1_3_levelsFollowTheirOrderAndCategoriesTheirCountThenName() {
        assertThat(AwardCountsHelper.byLevel(COUNTS)).containsExactly(new LevelCount(RecognitionLevel.FACULTY, 1),
            new LevelCount(RecognitionLevel.UNIVERSITY, 1), new LevelCount(RecognitionLevel.NATIONAL, 4));
        assertThat(AwardCountsHelper.byCategory(COUNTS)).containsExactly(new CategoryCount(MINISTRY, 2),
            new CategoryCount(STATE_PRIZE, 2), new CategoryCount(BEST_TEACHER, 1),
            new CategoryCount(FACULTY_AWARD, 1));
    }

    @Test
    void ac1_3_noCountsGiveEmptySeries() {
        assertThat(AwardCountsHelper.byYear(new YearRange(2026, 2026), List.of()))
            .containsExactly(new YearCount(2026, 0, Map.of()));
        assertThat(AwardCountsHelper.byLevel(List.of())).isEmpty();
        assertThat(AwardCountsHelper.byCategory(List.of())).isEmpty();
    }
}
