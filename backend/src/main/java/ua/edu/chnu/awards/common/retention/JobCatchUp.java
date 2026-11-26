package ua.edu.chnu.awards.common.retention;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs the nightly jobs once, in their catch-up order, {@code app.jobs.catch-up-delay} after a start when
 * {@code app.jobs.catch-up-on-start} is set. The jobs are idempotent, so a start without a missed night changes
 * nothing they would not change at night.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobCatchUp {

    private final List<CatchUpJob> jobs;
    private final JobsProperties properties;
    private final TaskScheduler scheduler;
    private final Clock clock;

    /**
     * Schedules the catch-up once the application is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void scheduleAtStart() {
        if (properties.catchUpOnStart()) {
            scheduler.schedule(this::runAll, clock.instant().plus(properties.catchUpDelay()));
            log.info("Catch-up of the nightly jobs in {}", properties.catchUpDelay());
        }
    }

    /**
     * Runs every job once in its order; a failing job is logged and the next one still runs.
     */
    public void runAll() {
        jobs.stream()
            .sorted(Comparator.comparingInt(CatchUpJob::catchUpOrder))
            .forEach(JobCatchUp::runOne);
    }

    private static void runOne(CatchUpJob job) {
        try {
            log.info("Catch-up {}: {}", job.catchUpName(), job.catchUp());
        } catch (Exception exception) {
            log.warn("Catch-up {} failed: {}", job.catchUpName(), exception.getMessage());
        }
    }
}
