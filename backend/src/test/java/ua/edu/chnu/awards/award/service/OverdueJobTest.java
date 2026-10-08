package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;

class OverdueJobTest {

    private static final Instant NOW = Instant.parse("2026-10-07T08:05:00Z");

    private final OverdueNotices notices = mock(OverdueNotices.class);
    private final OverdueJob job = new OverdueJob(notices, Clock.fixed(NOW, ZoneId.of("Europe/Kyiv")));

    @Test
    void ac2_1_aRunMarksAtTheClockTime() {
        when(notices.run(NOW)).thenReturn(3);

        assertThat(job.run()).isEqualTo(3);
    }

    @Test
    void ac2_1_aDatabaseFailureIsLoggedAndLeftToTheNextRun() {
        when(notices.run(NOW)).thenThrow(new QueryTimeoutException("timeout"));

        assertThat(job.run()).isZero();
    }
}
