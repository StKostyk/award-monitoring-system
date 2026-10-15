package ua.edu.chnu.awards.support;

import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import ua.edu.chnu.awards.common.event.EventProperties;
import ua.edu.chnu.awards.common.mail.MailDelivery;
import ua.edu.chnu.awards.notification.repository.NotificationRepository;
import ua.edu.chnu.awards.notification.service.DeliveryLog;
import ua.edu.chnu.awards.notification.service.DeliveryMetrics;
import ua.edu.chnu.awards.notification.service.NotificationKeys;
import ua.edu.chnu.awards.notification.service.NotificationPreferenceService;
import ua.edu.chnu.awards.notification.service.NotificationRows;

/**
 * Delivery logs for unit tests of the mail listeners.
 */
public final class TestDeliveries {

    /** The fixed moment of the clock the logs use. */
    public static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");
    /** The retry window of the logs. */
    public static final Duration RETRY_AFTER = Duration.ofMinutes(10);

    private TestDeliveries() {
    }

    /**
     * A log around a mocked mail server with a mocked repository.
     *
     * @param delivery    the mail server
     * @param preferences the preferences
     * @return the log
     */
    public static DeliveryLog log(MailDelivery delivery, NotificationPreferenceService preferences) {
        return log(delivery, preferences, mock(NotificationRepository.class), new DeliveryMetrics(
            new SimpleMeterRegistry()));
    }

    /**
     * A log around the given collaborators.
     *
     * @param delivery      the mail server
     * @param preferences   the preferences
     * @param notifications the repository
     * @param metrics       the counter
     * @return the log
     */
    public static DeliveryLog log(MailDelivery delivery, NotificationPreferenceService preferences,
                                  NotificationRepository notifications, DeliveryMetrics metrics) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        EventProperties events = new EventProperties(Duration.ofMinutes(1), RETRY_AFTER, RETRY_AFTER,
            Duration.ofDays(1), Duration.ofDays(1));
        return new DeliveryLog(delivery, rows(new ObjectMapper(), NOW), notifications, preferences, metrics, events,
            clock);
    }

    /**
     * Notification rows stamped at a fixed moment.
     *
     * @param mapper writes the payloads
     * @param now    the moment
     * @return the rows
     */
    public static NotificationRows rows(ObjectMapper mapper, Instant now) {
        return new NotificationRows(new NotificationKeys(), mapper, Clock.fixed(now, ZoneOffset.UTC));
    }
}
