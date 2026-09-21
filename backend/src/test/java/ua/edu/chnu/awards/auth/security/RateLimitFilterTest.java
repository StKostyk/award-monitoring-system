package ua.edu.chnu.awards.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import jakarta.servlet.ServletException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import ua.edu.chnu.awards.config.ProtectionProperties;

class RateLimitFilterTest {

    private static final Instant NOW = Instant.parse("2026-09-21T10:00:15Z");

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final RateLimitFilter filter = new RateLimitFilter(redis,
        new ProtectionProperties(5, Duration.ofMinutes(15), Duration.ofMinutes(30), 20),
        Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void ac42_requestsWithinTheLimitPassAndTheWindowKeyExpires() throws ServletException, IOException {
        String key = "auth:rate:203.0.113.7:" + NOW.getEpochSecond() / 60;
        when(values.increment(key)).thenReturn(1L);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("application/json"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(redis).expire(key, Duration.ofSeconds(120));
    }

    @Test
    void ac42_theTwentyFirstRequestIsRefusedWithRetryAfter() throws ServletException, IOException {
        when(values.increment(anyString())).thenReturn(21L);
        MockHttpServletResponse api = new MockHttpServletResponse();
        MockHttpServletResponse browser = new MockHttpServletResponse();

        filter.doFilter(request("application/json"), api, new MockFilterChain());
        filter.doFilter(request("text/html,application/xhtml+xml"), browser, new MockFilterChain());

        assertThat(api.getStatus()).isEqualTo(429);
        assertThat(api.getHeader("Retry-After")).isEqualTo("45");
        assertThat(api.getContentType()).isEqualTo("application/problem+json");
        assertThat(api.getContentAsString()).contains("urn:awards:problem:too-many-requests");
        assertThat(browser.getStatus()).isEqualTo(429);
        assertThat(browser.getHeader("Retry-After")).isEqualTo("45");
    }

    @Test
    void ac42_preflightRequestsAreNotCounted() throws ServletException, IOException {
        MockHttpServletRequest preflight = request("*/*");
        preflight.setMethod("OPTIONS");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(preflight, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(values, never()).increment(anyString());
    }

    @Test
    void redisOutageDoesNotLimit() throws ServletException, IOException {
        when(values.increment(anyString())).thenThrow(new QueryTimeoutException("down"));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("application/json"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    private static MockHttpServletRequest request(String accept) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("Accept", accept);
        return request;
    }
}
