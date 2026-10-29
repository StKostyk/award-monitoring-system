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
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.CorsProcessor;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import ua.edu.chnu.awards.common.EmailUtils;
import ua.edu.chnu.awards.common.limit.FixedWindowCounter;
import ua.edu.chnu.awards.common.limit.RateLimitMetrics;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.ClientRequest;

/**
 * Fixed one-minute window over one group of endpoints (authentication, sign-in form, public API), each group with
 * its own key prefix, budget and subject: the client address, or the account named in the sign-in form. Browsers
 * get the 429 error page, API clients a Problem Details body; both carry {@code Retry-After} and the CORS headers
 * the browser application needs to read the refusal. Without Redis nothing is limited.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    /** Redis key prefix of the per-address request windows of the authentication endpoints. */
    public static final String KEY_PREFIX = "auth:rate:";
    /** Redis key prefix of the per-account windows of the sign-in form. */
    public static final String ACCOUNT_KEY_PREFIX = "auth:account:";
    /** Redis key prefix of the per-address request windows of the public API. */
    public static final String PUBLIC_KEY_PREFIX = "public:rate:";
    private static final String BEARER = "Bearer ";
    private static final String USERNAME = "username";
    private final FixedWindowCounter counter;
    private final RateLimitMetrics metrics;
    private final Rule rule;
    private final CorsConfigurationSource cors;
    private final ObjectMapper objectMapper;
    private final CorsProcessor corsProcessor = new DefaultCorsProcessor();

    /**
     * Creates a limit over one group of endpoints.
     *
     * @param counter      the shared window counter
     * @param metrics      counts and logs the refusals
     * @param rule         the name, key prefix, budget and subject of this limit
     * @param cors         CORS rules applied to the refusal
     * @param objectMapper writes the Problem Details body
     */
    public RateLimitFilter(FixedWindowCounter counter, RateLimitMetrics metrics, Rule rule,
                           CorsConfigurationSource cors, ObjectMapper objectMapper) {
        this.counter = counter;
        this.metrics = metrics;
        this.rule = rule;
        this.cors = cors;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HttpMethod.OPTIONS.matches(request.getMethod())
            || rule.subject() == Subject.ANONYMOUS_ADDRESS && hasBearerToken(request)
            || rule.subject() == Subject.ACCOUNT && !HttpMethod.POST.matches(request.getMethod());
    }

    private static boolean hasBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization != null && authorization.regionMatches(true, 0, BEARER, 0, BEARER.length());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String subject = rule.subject() == Subject.ACCOUNT
            ? EmailUtils.normalize(request.getParameter(USERNAME))
            : ClientRequest.from(request).ip();
        long wait = subject.isEmpty() ? 0 : counter.secondsToWait(rule.keyPrefix(), subject, rule.requestsPerMinute());
        if (wait > 0) {
            metrics.refused(rule.name(), request.getRequestURI());
            refuse(request, response, wait);
            return;
        }
        chain.doFilter(request, response);
    }

    private void refuse(HttpServletRequest request, HttpServletResponse response, long retryAfter)
            throws IOException {
        if (!corsProcessor.processRequest(cors.getCorsConfiguration(request), request, response)) {
            return;
        }
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept != null && accept.contains(MediaType.TEXT_HTML_VALUE)) {
            response.sendError(HttpStatus.TOO_MANY_REQUESTS.value());
            return;
        }
        String detail = rule.subject() == Subject.ACCOUNT
            ? "Too many sign-in attempts for this account; try again in a minute"
            : "Too many requests from this address; try again in a minute";
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
            ApiProblemException.tooManyRequests(detail, retryAfter).toProblem());
    }

    /**
     * What one limit counts its requests by.
     */
    public enum Subject {
        /** The client address. */
        ADDRESS,
        /** The client address, for calls without a bearer token only; the resource server judges the others. */
        ANONYMOUS_ADDRESS,
        /** The account named in the {@code username} parameter of a POST, lower-cased and trimmed. */
        ACCOUNT
    }

    /**
     * One limit.
     *
     * @param name              the limit's name in the metric and the log, a {@link RateLimitMetrics} constant
     * @param keyPrefix         Redis key prefix of this limit's windows
     * @param requestsPerMinute requests one subject may send per minute
     * @param subject           what the requests are counted by
     */
    public record Rule(String name, String keyPrefix, int requestsPerMinute, Subject subject) {
    }
}
