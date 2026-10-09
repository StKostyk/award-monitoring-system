package ua.edu.chnu.awards.auth.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.CorsProcessor;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import ua.edu.chnu.awards.common.limit.FixedWindowCounter;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.ClientRequest;

/**
 * Fixed one-minute window per client address over one group of endpoints (authentication, public API), each group
 * with its own key prefix and budget. Browsers get the 429 error
 * page, API clients a Problem Details body; both carry {@code Retry-After} and the CORS headers the browser
 * application needs to read the refusal. Without Redis nothing is limited.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    /** Redis key prefix of the per-address request windows of the authentication endpoints. */
    public static final String KEY_PREFIX = "auth:rate:";
    /** Redis key prefix of the per-address request windows of the public API. */
    public static final String PUBLIC_KEY_PREFIX = "public:rate:";
    private final FixedWindowCounter counter;
    private final String keyPrefix;
    private final int requestsPerMinute;
    private final CorsConfigurationSource cors;
    private final ObjectMapper objectMapper;
    private final CorsProcessor corsProcessor = new DefaultCorsProcessor();

    /**
     * Creates a limit over one group of endpoints.
     *
     * @param counter           the shared window counter
     * @param keyPrefix         Redis key prefix of this group's windows
     * @param requestsPerMinute requests one client address may send per minute
     * @param cors              CORS rules applied to the refusal
     * @param objectMapper      writes the Problem Details body
     */
    public RateLimitFilter(FixedWindowCounter counter, String keyPrefix, int requestsPerMinute,
                           CorsConfigurationSource cors, ObjectMapper objectMapper) {
        this.counter = counter;
        this.keyPrefix = keyPrefix;
        this.requestsPerMinute = requestsPerMinute;
        this.cors = cors;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long wait = counter.secondsToWait(keyPrefix, ClientRequest.from(request).ip(), requestsPerMinute);
        if (wait > 0) {
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
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains(MediaType.TEXT_HTML_VALUE)) {
            response.sendError(HttpStatus.TOO_MANY_REQUESTS.value());
            return;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiProblemException.tooManyRequests(
            "Too many requests from this address; try again in a minute", retryAfter).toProblem());
    }
}
