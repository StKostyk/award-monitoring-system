package ua.edu.chnu.awards.gdpr.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.scheduling.support.CronExpression;

class NightlyScheduleTest {

    private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");

    private static final int DAYS = 3;

    @ParameterizedTest
    @CsvSource({
        "0 30 2 * * *, 2027-03-27",
        "0 0 4 * * *, 2027-03-27",
        "0 30 2 * * *, 2026-10-24",
        "0 0 4 * * *, 2026-10-24"
    })
    void ac1_2_theErasureAndRetentionRunsFireOnceANightAcrossTheClockChange(String cron, LocalDate from) {
        CronExpression expression = CronExpression.parse(cron);
        ZonedDateTime cursor = from.atStartOfDay(KYIV);
        List<LocalDate> nights = new ArrayList<>();
        for (int fired = 0; fired < DAYS; fired++) {
            cursor = expression.next(cursor);
            nights.add(cursor.toLocalDate());
        }

        assertThat(nights).containsExactly(from, from.plusDays(1), from.plusDays(2));
    }
}
