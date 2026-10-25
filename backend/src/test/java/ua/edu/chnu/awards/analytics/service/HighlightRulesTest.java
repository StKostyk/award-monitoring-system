package ua.edu.chnu.awards.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.analytics.config.AnalyticsProperties;
import ua.edu.chnu.awards.analytics.dto.Figure;
import ua.edu.chnu.awards.analytics.dto.UnitComparisonRow;
import ua.edu.chnu.awards.analytics.dto.UnitHighlight;
import ua.edu.chnu.awards.analytics.dto.UnitTotals;
import ua.edu.chnu.awards.organization.dto.OrganizationRef;
import ua.edu.chnu.awards.organization.entity.OrganizationType;

class HighlightRulesTest {

    private static final int MIN_GROUP_SIZE = 5;

    private final HighlightRules rules = new HighlightRules(new AnalyticsProperties(MIN_GROUP_SIZE, 500, 10,
        Duration.ofMinutes(5), Duration.ofDays(2)));

    @Test
    void ac1_8_aDepartmentWithoutAwardsIsNamedForThatAlone() {
        assertThat(types(row(1, 3, 0, 8), totals(40, 10))).containsExactly(UnitHighlight.Type.NO_AWARDS);
    }

    @Test
    void ac1_8_aDeclineNeedsFourPreviousAwardsAndAtMostHalfOfThem() {
        assertThat(types(row(1, 3, 2, 4), totals(2, 3))).containsExactly(UnitHighlight.Type.DECLINE);
        assertThat(types(row(1, 3, 3, 5), totals(3, 3))).isEmpty();
        assertThat(types(row(1, 3, 1, 3), totals(1, 3))).isEmpty();
    }

    @Test
    void ac1_8_aGrowthNeedsFourPreviousAwardsAndAtLeastHalfAsManyAgain() {
        assertThat(types(row(1, 3, 6, 4), totals(6, 3))).containsExactly(UnitHighlight.Type.GROWTH);
        assertThat(types(row(1, 3, 5, 4), totals(5, 3))).isEmpty();
        assertThat(types(row(1, 3, 9, 3), totals(9, 3))).isEmpty();
    }

    @Test
    void ac1_8_belowAverageNeedsTheMinimumGroupAndUnderHalfTheFacultyRate() {
        UnitTotals faculty = totals(100, 20);

        assertThat(types(row(1, MIN_GROUP_SIZE, 12, 12), faculty)).containsExactly(UnitHighlight.Type.BELOW_AVERAGE);
        assertThat(types(row(1, MIN_GROUP_SIZE, 13, 12), faculty)).isEmpty();
        assertThat(types(row(1, MIN_GROUP_SIZE - 1, 1, 1), faculty)).isEmpty();
    }

    @Test
    void ac1_8_rowsWithoutMembersAndTheFacultyLevelAreLeftOutAndGapsComeFirst() {
        UnitComparisonRow growing = row(1, 3, 8, 4);
        UnitComparisonRow empty = row(2, 3, 0, 0);
        UnitComparisonRow withoutMembers = row(3, 0, 0, 9);
        UnitComparisonRow facultyLevel = new UnitComparisonRow(unit(4), true, true, 3, Figure.of(0, 0), 0, null, 0,
            List.of());

        List<UnitHighlight> highlights = rules.of(List.of(growing, empty, withoutMembers, facultyLevel),
            totals(8, 9));

        assertThat(highlights).extracting(UnitHighlight::type, highlight -> highlight.unit().id(),
                UnitHighlight::current, UnitHighlight::previous)
            .containsExactly(tuple(UnitHighlight.Type.NO_AWARDS, 2L, 0L, 0L),
                tuple(UnitHighlight.Type.GROWTH, 1L, 8L, 4L));
    }

    @Test
    void ac1_7_aboveAverageNeedsTheMinimumGroupAndHalfAsManyAgainAsTheUniversityRate() {
        UnitTotals university = totals(100, 50);

        assertThat(universityTypes(row(1, 10, 30, 30), university)).containsExactly(UnitHighlight.Type.ABOVE_AVERAGE);
        assertThat(universityTypes(row(1, 10, 29, 29), university)).isEmpty();
        assertThat(universityTypes(row(1, MIN_GROUP_SIZE - 1, 30, 30), university)).isEmpty();
        assertThat(universityTypes(row(1, 10, 2, 2), university)).containsExactly(UnitHighlight.Type.BELOW_AVERAGE);
    }

    @Test
    void ac1_7_theFacultyPageNeverReceivesAboveAverage() {
        assertThat(types(row(1, 10, 30, 30), totals(100, 50))).isEmpty();
    }

    @Test
    void ac1_7_theUniversityLevelRowGetsNoHighlightAndStrengthsComeLast() {
        UnitComparisonRow strong = row(1, 10, 60, 30);
        UnitComparisonRow empty = row(2, 10, 0, 0);
        UnitComparisonRow universityLevel = new UnitComparisonRow(unit(3), true, true, 10, Figure.of(0, 9), 0, null,
            0, List.of());

        List<UnitHighlight> highlights = rules.ofUniversity(List.of(strong, empty, universityLevel), totals(60, 30));

        assertThat(highlights).extracting(UnitHighlight::type, highlight -> highlight.unit().id())
            .containsExactly(tuple(UnitHighlight.Type.NO_AWARDS, 2L), tuple(UnitHighlight.Type.GROWTH, 1L),
                tuple(UnitHighlight.Type.ABOVE_AVERAGE, 1L));
    }

    private List<UnitHighlight.Type> universityTypes(UnitComparisonRow row, UnitTotals totals) {
        return rules.ofUniversity(List.of(row), totals).stream().map(UnitHighlight::type).toList();
    }

    private List<UnitHighlight.Type> types(UnitComparisonRow row, UnitTotals totals) {
        return rules.of(List.of(row), totals).stream().map(UnitHighlight::type).toList();
    }

    private static UnitComparisonRow row(long id, long members, long current, long previous) {
        return new UnitComparisonRow(unit(id), false, true, members, Figure.of(current, previous), 0, null, 0,
            List.of());
    }

    private static OrganizationRef unit(long id) {
        return new OrganizationRef(id, "Department " + id, "Кафедра " + id, "D" + id, OrganizationType.DEPARTMENT);
    }

    private static UnitTotals totals(long approved, long members) {
        return new UnitTotals(Figure.of(approved, 0), Figure.of(0, 0), approved, 0, 0, members, null);
    }
}
