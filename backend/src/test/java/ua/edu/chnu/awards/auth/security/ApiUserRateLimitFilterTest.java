package ua.edu.chnu.awards.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import jakarta.servlet.ServletException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import ua.edu.chnu.awards.common.limit.FixedWindowCounter;
import ua.edu.chnu.awards.common.limit.RateLimitMetrics;
import ua.edu.chnu.awards.common.limit.RateLimitProperties;

class ApiUserRateLimitFilterTest {

    private static final Instant NOW = Instant.parse("2026-10-29T10:00:15Z");
    private static final long WINDOW = NOW.getEpochSecond() / 60;
    private static final int API_BUDGET = 600;
    private static final int EXPORT_BUDGET = 5;

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ApiUserRateLimitFilter filter = new ApiUserRateLimitFilter(
        new FixedWindowCounter(redis, Clock.fixed(NOW, ZoneOffset.UTC)), new RateLimitMetrics(registry),
        new RateLimitProperties(API_BUDGET, EXPORT_BUDGET), Jackson2ObjectMapperBuilder.json().build());

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ac2_4_requestsAreCountedByTheTokenSubjectAndTheOneOverTheBudgetIsRefused()
            throws ServletException, IOException {
        signIn("42");
        when(values.increment("api:rate:42:" + WINDOW)).thenReturn((long) API_BUDGET, API_BUDGET + 1L);
        MockFilterChain passed = new MockFilterChain();
        MockFilterChain stopped = new MockFilterChain();
        MockHttpServletResponse refused = new MockHttpServletResponse();

        filter.doFilter(get("/api/v1/awards"), new MockHttpServletResponse(), passed);
        filter.doFilter(get("/api/v1/awards"), refused, stopped);

        assertThat(passed.getRequest()).isNotNull();
        assertThat(stopped.getRequest()).isNull();
        assertThat(refused.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(refused.getHeader("Retry-After")).isEqualTo("45");
        assertThat(refused.getContentType()).isEqualTo("application/problem+json");
        assertThat(refused.getContentAsString()).contains("urn:awards:problem:too-many-requests");
        assertThat(registry.get("award.rate.limited").tag("limit", "api-user").counter().count()).isEqualTo(1);
    }

    @Test
    void ac2_4_callsWithoutAValidatedTokenAndExemptPathsAreNotCounted() throws ServletException, IOException {
        MockFilterChain anonymous = new MockFilterChain();
        filter.doFilter(get("/api/v1/organizations"), new MockHttpServletResponse(), anonymous);
        signIn("42");
        MockFilterChain publicApi = new MockFilterChain();
        MockFilterChain auth = new MockFilterChain();
        MockHttpServletRequest preflight = get("/api/v1/awards");
        preflight.setMethod("OPTIONS");

        filter.doFilter(get("/api/v1/public/achievements"), new MockHttpServletResponse(), publicApi);
        filter.doFilter(get("/api/v1/auth/sessions"), new MockHttpServletResponse(), auth);
        filter.doFilter(preflight, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(anonymous.getRequest()).isNotNull();
        assertThat(publicApi.getRequest()).isNotNull();
        assertThat(auth.getRequest()).isNotNull();
        verify(values, never()).increment(anyString());
    }

    @Test
    void ac2_5_theExportsShareOneSmallerWindowPerUser() throws ServletException, IOException {
        signIn("42");
        when(values.increment("api:rate:42:" + WINDOW)).thenReturn(1L);
        when(values.increment("export:rate:42:" + WINDOW)).thenReturn(4L, 5L, 6L);
        MockFilterChain report = new MockFilterChain();
        MockFilterChain unitExport = new MockFilterChain();
        MockFilterChain auditExport = new MockFilterChain();
        MockHttpServletResponse refused = new MockHttpServletResponse();

        filter.doFilter(get("/api/v1/analytics/university/report"), new MockHttpServletResponse(), report);
        filter.doFilter(get("/api/v1/analytics/units/7/export"), new MockHttpServletResponse(), unitExport);
        filter.doFilter(get("/api/v1/awards/9/audit-trail/export"), refused, auditExport);

        assertThat(report.getRequest()).isNotNull();
        assertThat(unitExport.getRequest()).isNotNull();
        assertThat(auditExport.getRequest()).isNull();
        assertThat(refused.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(refused.getContentAsString()).contains("reports and exports");
        assertThat(registry.get("award.rate.limited").tag("limit", "export").counter().count()).isEqualTo(1);
    }

    @Test
    void ac2_5_otherCallsAndThePersonalExportDoNotUseTheExportWindow() throws ServletException, IOException {
        signIn("42");
        when(values.increment(startsWith("api:rate:"))).thenReturn(1L);

        filter.doFilter(get("/api/v1/users/me/export"), new MockHttpServletResponse(), new MockFilterChain());
        filter.doFilter(get("/api/v1/analytics/university"), new MockHttpServletResponse(), new MockFilterChain());

        verify(values, never()).increment(startsWith("export:rate:"));
    }

    @Test
    void ac2_7_withoutRedisNoRequestIsRefused() throws ServletException, IOException {
        signIn("42");
        when(values.increment(anyString())).thenThrow(new QueryTimeoutException("down"));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(get("/api/v1/analytics/university/report"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    private static void signIn(String subject) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject(subject).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private static MockHttpServletRequest get(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr("203.0.113.7");
        return request;
    }
}
