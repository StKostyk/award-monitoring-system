package ua.edu.chnu.awards.support;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import ua.edu.chnu.awards.auth.security.ApiUserRateLimitFilter;
import ua.edu.chnu.awards.auth.security.RateLimitFilter;

/**
 * Base class for tests that boot the full application against real containers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ContainersConfiguration.class)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    private static final List<String> RATE_LIMIT_PREFIXES = List.of(RateLimitFilter.KEY_PREFIX,
        RateLimitFilter.ACCOUNT_KEY_PREFIX, RateLimitFilter.PUBLIC_KEY_PREFIX, ApiUserRateLimitFilter.KEY_PREFIX,
        ApiUserRateLimitFilter.EXPORT_KEY_PREFIX);

    @Autowired
    private StringRedisTemplate rateLimitRedis;

    /**
     * Starts every test with empty rate-limit windows, since the whole suite signs in from one address with a
     * few accounts.
     */
    @BeforeEach
    void resetRateLimitWindows() {
        RATE_LIMIT_PREFIXES.forEach(prefix -> rateLimitRedis.delete(rateLimitRedis.keys(prefix + "*")));
    }
}
