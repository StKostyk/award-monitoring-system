package ua.edu.chnu.awards.auth.security;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import ua.edu.chnu.awards.common.web.ClientRequest;
import ua.edu.chnu.awards.config.ProtectionProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Fixed one-minute window per client address over the authentication endpoints. Browsers get the 429 error
 * page, API clients a Problem Details body; both carry {@code Retry-After}. Without Redis nothing is limited.
 */
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    static final String KEY_PREFIX = "auth:rate:";
    static final String PROBLEM_BODY = "{\"type\":\"urn:awards:problem:too-many-requests\","
        + "\"title\":\"Too Many Requests\",\"status\":429,"
        + "\"detail\":\"Too many requests from this address; try again in a minute\"}";
    private static final long WINDOW_SECONDS = 60;
    private static final Duration KEY_TTL = Duration.ofSeconds(2 * WINDOW_SECONDS);

    private final StringRedisTemplate redis;
    private final ProtectionProperties properties;
    private final Clock clock;

    public RateLimitFilter(StringRedisTemplate redis, ProtectionProperties properties, Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = clock.instant().getEpochSecond();
        long window = now / WINDOW_SECONDS;
        String key = KEY_PREFIX + ClientRequest.from(request).ip() + ":" + window;
        if (countInWindow(key) > properties.requestsPerMinute()) {
            refuse(request, response, (window + 1) * WINDOW_SECONDS - now);
            return;
        }
        chain.doFilter(request, response);
    }

    private long countInWindow(String key) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, KEY_TTL);
            }
            return count == null ? 0 : count;
        } catch (DataAccessException e) {
            log.error("Redis unavailable; requests are not rate limited: {}", e.getMessage());
            return 0;
        }
    }

    private static void refuse(HttpServletRequest request, HttpServletResponse response, long retryAfter)
            throws IOException {
        response.setHeader("Retry-After", String.valueOf(Math.max(1, retryAfter)));
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains(MediaType.TEXT_HTML_VALUE)) {
            response.sendError(HttpStatus.TOO_MANY_REQUESTS.value());
            return;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(PROBLEM_BODY);
    }
}
