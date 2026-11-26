package ua.edu.chnu.awards.auth.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import ua.edu.chnu.awards.auth.security.RateLimitFilter;
import ua.edu.chnu.awards.auth.security.RateLimitFilter.Rule;
import ua.edu.chnu.awards.auth.security.RateLimitFilter.Subject;
import ua.edu.chnu.awards.common.limit.FixedWindowCounter;
import ua.edu.chnu.awards.common.limit.ProtectionProperties;
import ua.edu.chnu.awards.common.limit.RateLimitMetrics;

/**
 * Request limits ahead of the security chains: per address on the authentication endpoints and the public API,
 * and per account on the sign-in form, checked before the password is.
 */
@Configuration
@EnableConfigurationProperties(ProtectionProperties.class)
public class RateLimitConfig {

    private static final int BEFORE_SESSION_FILTER = SecurityProperties.DEFAULT_FILTER_ORDER - 2;

    private final FixedWindowCounter counter;
    private final RateLimitMetrics metrics;
    private final CorsConfigurationSource cors;
    private final ObjectMapper objectMapper;

    RateLimitConfig(FixedWindowCounter counter, RateLimitMetrics metrics,
                    @Qualifier("corsConfigurationSource") CorsConfigurationSource cors, ObjectMapper objectMapper) {
        this.counter = counter;
        this.metrics = metrics;
        this.cors = cors;
        this.objectMapper = objectMapper;
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(ProtectionProperties properties) {
        Rule rule = new Rule(RateLimitMetrics.AUTH_ADDRESS, RateLimitFilter.KEY_PREFIX,
            properties.requestsPerMinute(), Subject.ADDRESS);
        return registration("authRateLimit", rule, "/oauth2/token", "/login", "/api/v1/auth/*");
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> accountRateLimitFilter(ProtectionProperties properties) {
        Rule rule = new Rule(RateLimitMetrics.AUTH_ACCOUNT, RateLimitFilter.ACCOUNT_KEY_PREFIX,
            properties.accountRequestsPerMinute(), Subject.ACCOUNT);
        return registration("accountRateLimit", rule, "/login");
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> publicRateLimitFilter(ProtectionProperties properties) {
        Rule rule = new Rule(RateLimitMetrics.PUBLIC, RateLimitFilter.PUBLIC_KEY_PREFIX,
            properties.publicRequestsPerMinute(), Subject.ADDRESS);
        return registration("publicRateLimit", rule, "/api/v1/public/*");
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> organizationListRateLimitFilter(ProtectionProperties properties) {
        Rule rule = new Rule(RateLimitMetrics.PUBLIC, RateLimitFilter.PUBLIC_KEY_PREFIX,
            properties.publicRequestsPerMinute(), Subject.ANONYMOUS_ADDRESS);
        return registration("organizationListRateLimit", rule, "/api/v1/organizations");
    }

    private FilterRegistrationBean<RateLimitFilter> registration(String name, Rule rule, String... urlPatterns) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(
            new RateLimitFilter(counter, metrics, rule, cors, objectMapper));
        registration.setName(name);
        registration.addUrlPatterns(urlPatterns);
        registration.setOrder(BEFORE_SESSION_FILTER);
        return registration;
    }
}
