package ua.edu.chnu.awards.award.service;

import java.time.Clock;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs the overdue notice on {@code app.workflow.overdue-cron}, every hour at minute 5 Kyiv time by default, and
 * counts the queue once at startup so that the open and overdue gauges do not read zero until the first run.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverdueJob {

    private final OverdueNotices notices;
    private final Clock clock;

    /**
     * One run; a database failure is logged and the next run tries again.
     *
     * @return how many requests were marked
     */
    @Scheduled(cron = "${app.workflow.overdue-cron:0 5 * * * *}", zone = "Europe/Kyiv")
    public int run() {
        try {
            int marked = notices.run(clock.instant());
            if (marked > 0) {
                log.info("Overdue job marked {} requests", marked);
            }
            return marked;
        } catch (DataAccessException exception) {
            log.warn("Overdue job failed: {}", exception.getMessage());
            return 0;
        }
    }

    /**
     * Counts the queue for the gauges once the application is ready; a database failure is logged and the next run
     * counts again.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void countAtStart() {
        try {
            notices.refreshGauges(clock.instant());
        } catch (DataAccessException exception) {
            log.warn("Queue count at startup failed: {}", exception.getMessage());
        }
    }
}
