package ua.edu.chnu.awards.common.event;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import lombok.extern.slf4j.Slf4j;

/**
 * Resubmits the event publications whose listener failed, gives up on them after a day and deletes them after a
 * month; the gauges {@value #INCOMPLETE} and {@value #ABANDONED} show what is left.
 */
@Component
@Slf4j
public class PublicationRetry {

    static final String INCOMPLETE = "award.events.incomplete";
    static final String ABANDONED = "award.events.abandoned";
    private static final String COUNT_INCOMPLETE =
        "SELECT count(*) FROM event_publication WHERE completion_date IS NULL";
    private static final String COUNT_OLDER = COUNT_INCOMPLETE + " AND publication_date < ?";
    private static final String DELETE_OLDER =
        "DELETE FROM event_publication WHERE completion_date IS NULL AND publication_date < ?";

    private final IncompleteEventPublications publications;
    private final JdbcTemplate jdbc;
    private final EventProperties properties;
    private final Clock clock;

    /**
     * Creates the job and registers both gauges.
     *
     * @param publications the registry's incomplete publications
     * @param jdbc the JDBC access to the registry table
     * @param properties the retry settings
     * @param clock the application clock
     * @param registry the meter registry
     */
    public PublicationRetry(IncompleteEventPublications publications, JdbcTemplate jdbc, EventProperties properties,
                            Clock clock, MeterRegistry registry) {
        this.publications = publications;
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
        Gauge.builder(INCOMPLETE, this, PublicationRetry::incomplete)
            .description("Event publications whose listener has not completed").register(registry);
        Gauge.builder(ABANDONED, this, PublicationRetry::abandoned)
            .description("Incomplete event publications no longer retried").register(registry);
    }

    /**
     * Deletes the publications kept long enough, reports the abandoned ones and resubmits those inside the retry
     * window; the first run after a start picks up what an earlier run of the application left.
     */
    @Scheduled(initialDelayString = "${app.events.first-run:PT1M}",
        fixedDelayString = "${app.events.retry-interval:PT10M}")
    public void run() {
        Instant now = clock.instant();
        int deleted = jdbc.update(DELETE_OLDER, Timestamp.from(now.minus(properties.keepFailed())));
        if (deleted > 0) {
            log.warn("Deleted {} event publications incomplete for longer than {}", deleted,
                properties.keepFailed());
        }
        long abandoned = count(COUNT_OLDER, now.minus(properties.giveUpAfter()));
        if (abandoned > 0) {
            log.error("{} event publications are incomplete for longer than {} and no longer retried", abandoned,
                properties.giveUpAfter());
        }
        publications.resubmitIncompletePublications(publication -> inRetryWindow(publication, now));
    }

    boolean inRetryWindow(EventPublication publication, Instant now) {
        Instant published = publication.getPublicationDate();
        return !published.isAfter(now.minus(properties.retryAfter()))
            && published.isAfter(now.minus(properties.giveUpAfter()));
    }

    double incomplete() {
        Long count = jdbc.queryForObject(COUNT_INCOMPLETE, Long.class);
        return count == null ? 0 : count;
    }

    double abandoned() {
        return count(COUNT_OLDER, clock.instant().minus(properties.giveUpAfter()));
    }

    private long count(String sql, Instant before) {
        Long count = jdbc.queryForObject(sql, Long.class, Timestamp.from(before));
        return count == null ? 0 : count;
    }
}
