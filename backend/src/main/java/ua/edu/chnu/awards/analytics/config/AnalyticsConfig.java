package ua.edu.chnu.awards.analytics.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the analytics limits.
 */
@Configuration
@EnableConfigurationProperties(AnalyticsProperties.class)
public class AnalyticsConfig {
}
