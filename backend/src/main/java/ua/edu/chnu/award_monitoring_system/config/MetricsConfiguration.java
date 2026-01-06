package ua.edu.chnu.award_monitoring_system.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Micrometer metrics configuration for Award Monitoring System.
 * 
 * <p>Configures common tags, meter filters, and timed aspects for
 * comprehensive application metrics collection.</p>
 * 
 * @author Stefan Kostyk
 * @since 0.15.0
 */
@Configuration
public class MetricsConfiguration {

    @Value("${spring.application.name:award-monitoring-system}")
    private String applicationName;

    @Value("${spring.profiles.active:default}")
    private String environment;

    /**
     * Customizes all meter registries with common tags for identification.
     *
     * @return customizer that adds application and environment tags
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags("application", applicationName)
                .commonTags("environment", environment);
    }

    /**
     * Enables @Timed annotation support on methods for timing metrics.
     *
     * @param registry the meter registry
     * @return timed aspect bean
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    /**
     * Configures meter filters for customizing metric collection.
     *
     * @return meter filter for URI normalization and exclusions
     */
    @Bean
    public MeterFilter meterFilter() {
        return MeterFilter.deny(id -> {
            String uri = id.getTag("uri");
            // Exclude actuator endpoints from request metrics to reduce noise
            return uri != null && uri.startsWith("/actuator");
        });
    }
}

