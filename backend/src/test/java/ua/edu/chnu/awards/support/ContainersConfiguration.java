package ua.edu.chnu.awards.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared PostgreSQL and Redis containers for integration and functional tests.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ContainersConfiguration {

    private static final int REDIS_PORT = 6379;
    private static final int SMTP_PORT = 1025;
    private static final int MAILPIT_HTTP_PORT = 8025;

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(REDIS_PORT);
    }

    @Bean
    GenericContainer<?> mailpitContainer() {
        return new GenericContainer<>(DockerImageName.parse("axllent/mailpit:latest"))
            .withExposedPorts(SMTP_PORT, MAILPIT_HTTP_PORT);
    }

    @Bean
    DynamicPropertyRegistrar mailProperties(GenericContainer<?> mailpitContainer) {
        return registry -> {
            registry.add("spring.mail.host", mailpitContainer::getHost);
            registry.add("spring.mail.port", () -> mailpitContainer.getMappedPort(SMTP_PORT));
            registry.add("mailpit.api-url", () -> "http://" + mailpitContainer.getHost() + ":"
                + mailpitContainer.getMappedPort(MAILPIT_HTTP_PORT));
        };
    }
}
