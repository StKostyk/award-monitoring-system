package ua.edu.chnu.awards.gdpr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;

class ErasureJobTest {

    private static final Instant NOW = Instant.parse("2026-10-16T00:30:00Z");

    private final ErasureRequestService erasures = mock(ErasureRequestService.class);
    private final ErasureRunner runner = mock(ErasureRunner.class);
    private final ErasureJob job = new ErasureJob(erasures, runner, Clock.fixed(NOW, ZoneId.of("Europe/Kyiv")));

    @Test
    void ac4_6_aRunRemindsAtTheClockTime() {
        when(erasures.warnDue(NOW)).thenReturn(2);

        assertThat(job.run()).isEqualTo(2);
    }

    @Test
    void ac4_6_aDatabaseFailureIsLoggedAndLeftToTheNextRun() {
        when(erasures.warnDue(NOW)).thenThrow(new QueryTimeoutException("timeout"));
        when(runner.runDue(NOW)).thenThrow(new QueryTimeoutException("timeout"));

        assertThat(job.run()).isZero();
    }

    @Test
    void ac5_1_aRunErasesDueAccountsEvenWhenTheRemindersFail() {
        when(erasures.warnDue(NOW)).thenThrow(new QueryTimeoutException("timeout"));
        when(runner.runDue(NOW)).thenReturn(3);

        assertThat(job.run()).isEqualTo(3);
    }
}
