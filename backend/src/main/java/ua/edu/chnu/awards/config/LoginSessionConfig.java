package ua.edu.chnu.awards.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.ConcurrentSessionFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import ua.edu.chnu.awards.auth.security.RateLimitFilter;
import ua.edu.chnu.awards.auth.security.RetryRequestSessionExpiredStrategy;

/**
 * Protection of the sign-in path: login sessions tracked so a password reset can end them everywhere, and a
 * per-address request limit on the authentication endpoints.
 */
@Configuration
@EnableConfigurationProperties(ProtectionProperties.class)
public class LoginSessionConfig {

    private static final int BEFORE_SECURITY_CHAIN = SecurityProperties.DEFAULT_FILTER_ORDER - 1;
    private static final int BEFORE_SESSION_FILTER = BEFORE_SECURITY_CHAIN - 1;

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    /**
     * Invalidates an expired session before the authorization endpoint could issue a code for it; the session
     * management of the login chain runs too late for that endpoint.
     *
     * @param sessionRegistry registry filled by the login chain
     * @return the filter registration
     */
    @Bean
    FilterRegistrationBean<ConcurrentSessionFilter> expiredLoginSessionFilter(SessionRegistry sessionRegistry) {
        FilterRegistrationBean<ConcurrentSessionFilter> registration = new FilterRegistrationBean<>(
            new ConcurrentSessionFilter(sessionRegistry, new RetryRequestSessionExpiredStrategy()));
        registration.addUrlPatterns("/oauth2/authorize", "/login");
        registration.setOrder(BEFORE_SECURITY_CHAIN);
        return registration;
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(StringRedisTemplate redis, ProtectionProperties properties,
                                                            Clock clock) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(
            new RateLimitFilter(redis, properties, clock));
        registration.addUrlPatterns("/oauth2/token", "/login", "/api/v1/auth/*");
        registration.setOrder(BEFORE_SESSION_FILTER);
        return registration;
    }
}
