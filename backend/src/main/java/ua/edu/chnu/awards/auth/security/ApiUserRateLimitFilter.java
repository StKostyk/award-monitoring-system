package ua.edu.chnu.awards.auth.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import ua.edu.chnu.awards.common.limit.FixedWindowCounter;
import ua.edu.chnu.awards.common.limit.RateLimitMetrics;
import ua.edu.chnu.awards.common.limit.RateLimitProperties;
import ua.edu.chnu.awards.common.web.ApiProblemException;

/**
 * Fixed one-minute windows per signed-in user over the API, counted by the user id of the validated access token,
 * and a second, smaller window over the report and export endpoints. Runs after the bearer token filter, so calls
 * without a valid token are not counted; the public and authentication endpoints keep their own per-address limits.
 * The refusal is a Problem Details body with {@code Retry-After}; the CORS filter ahead of it has already set the
 * CORS headers. Without Redis nothing is limited.
 */
public class ApiUserRateLimitFilter extends OncePerRequestFilter {

    /** Redis key prefix of the per-user API windows. */
    public static final String KEY_PREFIX = "api:rate:";
    /** Redis key prefix of the per-user report and export windows. */
    public static final String EXPORT_KEY_PREFIX = "export:rate:";
    private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();
    private static final RequestMatcher UNCOUNTED = new OrRequestMatcher(PATHS.matcher("/api/v1/public/**"),
        PATHS.matcher("/api/v1/auth/**"), PATHS.matcher("/actuator/**"));
    private static final RequestMatcher EXPORTS = new OrRequestMatcher(
        PATHS.matcher(HttpMethod.GET, "/api/v1/analytics/university/report"),
        PATHS.matcher(HttpMethod.GET, "/api/v1/analytics/units/*/export"),
        PATHS.matcher(HttpMethod.GET, "/api/v1/awards/*/audit-trail/export"));
    private final FixedWindowCounter counter;
    private final RateLimitMetrics metrics;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Creates the per-user limits.
     *
     * @param counter      the shared window counter
     * @param metrics      counts and logs the refusals
     * @param properties   the API and export budgets
     * @param objectMapper writes the Problem Details body
     */
    public ApiUserRateLimitFilter(FixedWindowCounter counter, RateLimitMetrics metrics,
                                  RateLimitProperties properties, ObjectMapper objectMapper) {
        this.counter = counter;
        this.metrics = metrics;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HttpMethod.OPTIONS.matches(request.getMethod()) || UNCOUNTED.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token)) {
            chain.doFilter(request, response);
            return;
        }
        String userId = token.getToken().getSubject();
        long wait = counter.secondsToWait(KEY_PREFIX, userId, properties.apiRequestsPerMinute());
        if (wait > 0) {
            refuse(request, response, RateLimitMetrics.API_USER, wait);
            return;
        }
        if (EXPORTS.matches(request)) {
            long exportWait = counter.secondsToWait(EXPORT_KEY_PREFIX, userId, properties.exportRequestsPerMinute());
            if (exportWait > 0) {
                refuse(request, response, RateLimitMetrics.EXPORT, exportWait);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void refuse(HttpServletRequest request, HttpServletResponse response, String limit, long retryAfter)
            throws IOException {
        metrics.refused(limit, request.getRequestURI());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        String detail = RateLimitMetrics.EXPORT.equals(limit)
            ? "Too many reports and exports; try again in a minute"
            : "Too many requests; try again in a minute";
        objectMapper.writeValue(response.getOutputStream(),
            ApiProblemException.tooManyRequests(detail, retryAfter).toProblem());
    }
}
