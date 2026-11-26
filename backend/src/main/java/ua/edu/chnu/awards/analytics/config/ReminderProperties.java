package ua.edu.chnu.awards.analytics.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the reminders sent to departments.
 *
 * @param unitInterval the shortest time between two reminders of one department
 */
@ConfigurationProperties(prefix = "app.reminders")
public record ReminderProperties(@DefaultValue("P7D") Duration unitInterval) {

    /**
     * Refuses an interval that is not positive.
     *
     * @param unitInterval the shortest time between two reminders of one department
     */
    public ReminderProperties {
        if (unitInterval == null || !unitInterval.isPositive()) {
            throw new IllegalArgumentException("app.reminders.unit-interval must be positive: " + unitInterval);
        }
    }
}
