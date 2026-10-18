package ua.edu.chnu.awards.gdpr.service;

import java.time.Clock;
import java.util.function.IntSupplier;

import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The nightly erasure run: reminds people whose erasure is near, then erases the accounts whose erasure is due.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErasureJob {

    private final ErasureRequestService erasures;
    private final ErasureRunner runner;
    private final Clock clock;

    /**
     * One run; a database failure is logged and the next run tries again.
     *
     * @return how many reminders were queued and accounts erased
     */
    @Scheduled(cron = "${app.erasure.cron:0 30 2 * * *}", zone = "Europe/Kyiv")
    public int run() {
        int warned = attempt("reminders", () -> erasures.warnDue(clock.instant()));
        int erased = attempt("erasures", () -> runner.runDue(clock.instant()));
        if (warned + erased > 0) {
            log.info("Erasure job queued {} reminders and erased {} accounts", warned, erased);
        }
        return warned + erased;
    }

    private static int attempt(String part, IntSupplier step) {
        try {
            return step.getAsInt();
        } catch (DataAccessException exception) {
            log.warn("Erasure job failed at {}: {}", part, exception.getMessage());
            return 0;
        }
    }
}
