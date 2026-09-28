package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import ua.edu.chnu.awards.common.web.FieldViolation;

class AwardDateRulesTest {

    private static final LocalDate KYIV_TODAY = LocalDate.of(2026, 9, 28);

    private final AwardDateRules rules = new AwardDateRules(Clock.fixed(Instant.parse("2026-09-27T21:30:00Z"),
        ZoneId.of("Europe/Kyiv")));

    @Test
    void ac2_1_todayIsTheKyivDayWhileUtcIsStillYesterday() {
        assertThat(rules.today()).isEqualTo(KYIV_TODAY);
        assertThat(rules.check(KYIV_TODAY)).isEmpty();
        assertThat(rules.check(KYIV_TODAY.plusDays(1))).get()
            .isEqualTo(new FieldViolation("awardDate", "future", "The award date cannot be in the future"));
    }

    @Test
    void ac2_2_exactlyFiftyYearsAgoIsAcceptedAndOneDayMoreIsNot() {
        assertThat(rules.check(LocalDate.of(1976, 9, 28))).isEmpty();
        assertThat(rules.check(LocalDate.of(1976, 9, 27))).get()
            .extracting(FieldViolation::field, FieldViolation::code).containsExactly("awardDate", "too-old");
    }

    @Test
    void ac2_2_aLeapDayFiftyYearsLaterComparesWithTheLastDayOfFebruary() {
        AwardDateRules leap = new AwardDateRules(Clock.fixed(Instant.parse("2028-02-29T10:00:00Z"),
            ZoneId.of("Europe/Kyiv")));

        assertThat(leap.check(LocalDate.of(1978, 2, 28))).isEmpty();
        assertThat(leap.check(LocalDate.of(1978, 2, 27))).isPresent();
    }

    @Test
    void ac2_1_anEmptyDateIsNotChecked() {
        assertThat(rules.check(null)).isEmpty();
        assertThat(rules.isRecent(null)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"2026-09-28, true", "2026-08-30, true", "2026-08-29, false", "2025-09-28, false",
        "2026-09-29, false"})
    void ac2_3_theLastThirtyDaysAreRecent(LocalDate date, boolean recent) {
        assertThat(rules.isRecent(date)).isEqualTo(recent);
    }
}
