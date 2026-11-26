package ua.edu.chnu.awards.common.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.scheduling.TaskScheduler;

class JobCatchUpTest {

    private static final Instant NOW = Instant.parse("2026-11-10T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Europe/Kyiv"));

    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final CatchUpJob erasure = job("erasure", CatchUpOrderConstants.ERASURE);
    private final CatchUpJob retention = job("retention", CatchUpOrderConstants.RETENTION);
    private final CatchUpJob sweep = job("document sweep", CatchUpOrderConstants.DOCUMENT_SWEEP);

    @Test
    void ac2_6_aStartSchedulesTheCatchUpAfterTheDelay() {
        JobCatchUp catchUp = catchUp(new JobsProperties(true, Duration.ofMinutes(1)));

        catchUp.scheduleAtStart();

        verify(scheduler).schedule(any(Runnable.class), eq(NOW.plus(Duration.ofMinutes(1))));
    }

    @Test
    void ac2_6_noCatchUpWhenTheSettingIsOff() {
        JobCatchUp catchUp = catchUp(new JobsProperties(false, Duration.ofMinutes(1)));

        catchUp.scheduleAtStart();

        verify(scheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void ac2_6_theJobsRunOnceInTheOrderErasureRetentionSweep() {
        JobCatchUp catchUp = catchUp(new JobsProperties(true, Duration.ofMinutes(1)));

        catchUp.runAll();

        InOrder order = inOrder(erasure, retention, sweep);
        order.verify(erasure).catchUp();
        order.verify(retention).catchUp();
        order.verify(sweep).catchUp();
    }

    @Test
    void ac2_6_aFailingJobDoesNotStopTheOthers() {
        when(erasure.catchUp()).thenThrow(new IllegalStateException("database down"));
        JobCatchUp catchUp = catchUp(new JobsProperties(true, Duration.ofMinutes(1)));

        catchUp.runAll();

        verify(retention).catchUp();
        verify(sweep).catchUp();
    }

    @Test
    void ac2_6_aNegativeDelayIsRefused() {
        assertThatThrownBy(() -> new JobsProperties(true, Duration.ofSeconds(-1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(new JobsProperties(true, Duration.ZERO).catchUpDelay()).isZero();
    }

    private JobCatchUp catchUp(JobsProperties properties) {
        return new JobCatchUp(List.of(sweep, erasure, retention), properties, scheduler, CLOCK);
    }

    private static CatchUpJob job(String name, int order) {
        CatchUpJob job = mock(CatchUpJob.class);
        when(job.catchUpName()).thenReturn(name);
        when(job.catchUpOrder()).thenReturn(order);
        when(job.catchUp()).thenReturn("done");
        return job;
    }
}
