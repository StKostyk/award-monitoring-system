package ua.edu.chnu.awards.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.TimeoutOptions;

/**
 * Shared clock (injectable for tests), asynchronous execution for listeners and Redis client behaviour.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class InfrastructureConfig {

    /** Calendar days (role validity, delegations) are Kyiv days. */
    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("Europe/Kyiv"));
    }

    /**
     * Redis commands fail at once while the connection is down instead of waiting for the command timeout, so
     * the fail-open paths (login counters, request limits, reuse detection) do not stall requests.
     *
     * @return the Lettuce customizer
     */
    @Bean
    LettuceClientConfigurationBuilderCustomizer rejectCommandsWhileDisconnected() {
        return builder -> builder.clientOptions(ClientOptions.builder()
            .timeoutOptions(TimeoutOptions.enabled())
            .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
            .build());
    }
}
