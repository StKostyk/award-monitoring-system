package ua.edu.chnu.awards.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Shared clock (injectable for tests) and asynchronous execution for listeners.
 */
@Configuration
@EnableAsync
public class InfrastructureConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
