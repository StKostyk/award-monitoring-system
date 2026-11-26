package ua.edu.chnu.awards.notification.service;

import java.util.Locale;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import ua.edu.chnu.awards.notification.entity.NotificationChannel;
import ua.edu.chnu.awards.notification.entity.NotificationType;

import lombok.RequiredArgsConstructor;

/**
 * Counts notification deliveries per channel, type and outcome.
 */
@Component
@RequiredArgsConstructor
public class DeliveryMetrics {

    static final String DELIVERED = "award.notifications.delivered";

    private final MeterRegistry registry;

    /**
     * Counts one delivery attempt.
     *
     * @param channel the channel
     * @param type    the notification type
     * @param outcome what happened
     */
    public void count(NotificationChannel channel, NotificationType type, Outcome outcome) {
        Counter.builder(DELIVERED)
            .description("Notification deliveries per channel, type and outcome")
            .tag("channel", tag(channel))
            .tag("type", type.name())
            .tag("outcome", tag(outcome))
            .register(registry)
            .increment();
    }

    private static String tag(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    /**
     * What happened to one delivery.
     */
    public enum Outcome {
        /** Written, or accepted by the mail server. */
        SENT,
        /** The mail server did not accept it; the publication is retried. */
        FAILED,
        /** Not delivered again: written or sent before, being sent, or no recipient. */
        SKIPPED,
        /** The recipient switched the type off for the channel. */
        OFF
    }
}
