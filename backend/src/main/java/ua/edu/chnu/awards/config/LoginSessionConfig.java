package ua.edu.chnu.awards.config;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.session.ConcurrentSessionFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.web.cors.CorsConfigurationSource;

import ua.edu.chnu.awards.auth.security.AccountStatusChecker;
import ua.edu.chnu.awards.auth.security.JpaUserDetailsService;
import ua.edu.chnu.awards.auth.security.LockedAccountChecker;
import ua.edu.chnu.awards.auth.security.RateLimitFilter;
import ua.edu.chnu.awards.auth.security.RetryRequestSessionExpiredStrategy;

/**
 * The sign-in path: password checking with lock and status checks, login sessions tracked so a password reset
 * can end them everywhere, the request cache, and a per-address request limit on the authentication endpoints.
 */
@Configuration
@EnableConfigurationProperties(ProtectionProperties.class)
public class LoginSessionConfig {

    private static final int BEFORE_SECURITY_CHAIN = SecurityProperties.DEFAULT_FILTER_ORDER - 1;
    private static final int BEFORE_SESSION_FILTER = BEFORE_SECURITY_CHAIN - 1;
    private static final int BCRYPT_STRENGTH = 12;

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
    DaoAuthenticationProvider daoAuthenticationProvider(JpaUserDetailsService userDetailsService,
                                                        PasswordEncoder passwordEncoder,
                                                        AccountStatusChecker statusChecker,
                                                        LockedAccountChecker lockChecker) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setPreAuthenticationChecks(lockChecker);
        provider.setPostAuthenticationChecks(statusChecker);
        return provider;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    /**
     * Only an interrupted authorize request is worth resuming after login; any other saved request would land the
     * user on a page the authorization server does not serve.
     *
     * @return request cache limited to {@code /oauth2/authorize}
     */
    @Bean
    RequestCache authorizeRequestCache() {
        HttpSessionRequestCache cache = new HttpSessionRequestCache();
        cache.setRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher("/oauth2/authorize"));
        return cache;
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(StringRedisTemplate redis, ProtectionProperties properties,
                                                            Clock clock,
                                                            @Qualifier("corsConfigurationSource")
                                                            CorsConfigurationSource cors) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(
            new RateLimitFilter(redis, properties, clock, cors));
        registration.addUrlPatterns("/oauth2/token", "/login", "/api/v1/auth/*");
        registration.setOrder(BEFORE_SESSION_FILTER);
        return registration;
    }
}
