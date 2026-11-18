package ua.edu.chnu.awards.analytics.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the analytics limits and the reminder settings.
 */
@Configuration
@EnableConfigurationProperties({AnalyticsProperties.class, ReminderProperties.class})
public class AnalyticsConfig {
}
