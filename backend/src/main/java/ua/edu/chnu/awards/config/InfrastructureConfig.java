package ua.edu.chnu.awards.config;

import java.time.Clock;
import java.time.ZoneId;
import java.util.concurrent.Executor;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.TimeoutOptions;

import ua.edu.chnu.awards.common.event.EventProperties;
import ua.edu.chnu.awards.common.limit.FixedWindowCounter;
import ua.edu.chnu.awards.common.mail.UndeliveredMailAsyncHandler;
import ua.edu.chnu.awards.common.privacy.PrivacyNoticeProperties;
import ua.edu.chnu.awards.common.retention.RetentionProperties;

/**
 * Shared clock (injectable for tests), workflow timing, event publication retry, asynchronous execution for
 * listeners, Redis client behaviour and the request windows.
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties({EventProperties.class, PrivacyNoticeProperties.class, RetentionProperties.class})
@EnableScheduling
public class InfrastructureConfig {

    /** Calendar days (role validity, delegations) are Kyiv days. */
    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("Europe/Kyiv"));
    }

    /**
     * Asynchronous listeners run on the application task executor; an undelivered mail they rethrow is not logged
     * twice.
     *
     * @param executor the executor configured by {@code spring.task.execution}
     * @return the asynchronous execution settings
     */
    @Bean
    AsyncConfigurer asyncConfigurer(
            @Qualifier(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME) Executor executor) {
        return new AsyncConfigurer() {
            @Override
            public Executor getAsyncExecutor() {
                return executor;
            }

            @Override
            public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
                return new UndeliveredMailAsyncHandler();
            }
        };
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

    /**
     * One-minute request windows in Redis, shared by the sign-in limit and the upload rate.
     *
     * @param redis the window counters
     * @param clock the time of the window
     * @return the counter
     */
    @Bean
    FixedWindowCounter fixedWindowCounter(StringRedisTemplate redis, Clock clock) {
        return new FixedWindowCounter(redis, clock);
    }
}
