package ua.edu.chnu.awards.gdpr.service;

import java.time.Clock;

import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The nightly erasure run: reminds people whose erasure is near.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErasureJob {

    private final ErasureRequestService erasures;
    private final Clock clock;

    /**
     * One run; a database failure is logged and the next run tries again.
     *
     * @return how many reminders were queued
     */
    @Scheduled(cron = "${app.erasure.cron:0 30 3 * * *}", zone = "Europe/Kyiv")
    public int run() {
        try {
            int warned = erasures.warnDue(clock.instant());
            if (warned > 0) {
                log.info("Erasure job queued {} reminders", warned);
            }
            return warned;
        } catch (DataAccessException exception) {
            log.warn("Erasure job failed: {}", exception.getMessage());
            return 0;
        }
    }
}
