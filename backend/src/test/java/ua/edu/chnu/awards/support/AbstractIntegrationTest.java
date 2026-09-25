package ua.edu.chnu.awards.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for tests that boot the full application against real containers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ContainersConfiguration.class)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    private static final String RATE_LIMIT_KEYS = "auth:rate:*";

    @Autowired
    private StringRedisTemplate rateLimitRedis;

    /**
     * Starts every test with empty rate-limit windows, since the whole suite signs in from one address.
     */
    @BeforeEach
    void resetRateLimitWindows() {
        rateLimitRedis.delete(rateLimitRedis.keys(RATE_LIMIT_KEYS));
    }
}
