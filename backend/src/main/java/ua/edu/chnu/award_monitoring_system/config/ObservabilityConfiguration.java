package ua.edu.chnu.award_monitoring_system.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Observability configuration for distributed tracing and observation.
 * 
 * <p>Enables @Observed annotation support for automatic span creation
 * in distributed tracing systems like Jaeger.</p>
 * 
 * @author Stefan Kostyk
 * @since 0.15.0
 */
@Configuration
public class ObservabilityConfiguration {

    /**
     * Enables @Observed annotation support for automatic observation.
     *
     * @param observationRegistry the observation registry
     * @return observed aspect bean
     */
    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }
}

