package ua.edu.chnu.awards.common.limit;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Counts requests refused by a rate limit, exposed as {@code award_rate_limited_total{limit}}, and logs each
 * refusal with the limit and the path only, never the client address or the account.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitMetrics {

    /** The per-address limit of the authentication endpoints. */
    public static final String AUTH_ADDRESS = "auth-address";
    /** The per-account limit of the sign-in form. */
    public static final String AUTH_ACCOUNT = "auth-account";
    /** The per-address limit of the public API. */
    public static final String PUBLIC = "public";
    /** The per-user limit of the signed-in API. */
    public static final String API_USER = "api-user";
    /** The per-user limit of the report and export endpoints. */
    public static final String EXPORT = "export";
    /** The per-user limit of document uploads. */
    public static final String UPLOAD = "upload";

    static final String REFUSED = "award.rate.limited";

    private final MeterRegistry registry;

    /**
     * Counts and logs one refused request.
     *
     * @param limit the limit that refused it, one of the constants of this class
     * @param path  the request path
     */
    public void refused(String limit, String path) {
        log.warn("Rate limit {} refused {}", limit, path);
        Counter.builder(REFUSED)
            .description("Requests refused by a rate limit")
            .tag("limit", limit)
            .register(registry)
            .increment();
    }
}
