package ua.edu.chnu.awards.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
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

    /** MinIO image of the Compose files (the official images are no longer published). */
    public static final String MINIO_IMAGE = "cgr.dev/chainguard/minio:latest";

    /** Static KMS key of the test MinIO (not a secret: test containers only). */
    public static final String MINIO_KMS_KEY = "award-test-key:NRZ8tsvRYlQLw2e/3c3qy9IwuhHw6f8IhiOTZ40C4dM=";

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
            .withEnv("MP_SMTP_DISABLE_RDNS", "true")
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

    @Bean
    MinIOContainer minioContainer() {
        return new MinIOContainer(DockerImageName.parse(MINIO_IMAGE).asCompatibleSubstituteFor("minio/minio"))
            .withEnv("MINIO_KMS_SECRET_KEY", MINIO_KMS_KEY)
            .withCreateContainerCmdModifier(command -> command.withUser("0"));
    }

    @Bean
    DynamicPropertyRegistrar storageProperties(MinIOContainer minioContainer) {
        return registry -> {
            registry.add("app.documents.storage.endpoint", minioContainer::getS3URL);
            registry.add("app.documents.storage.access-key", minioContainer::getUserName);
            registry.add("app.documents.storage.secret-key", minioContainer::getPassword);
        };
    }
}
