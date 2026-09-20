package ua.edu.chnu.awards.config;

import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.ConcurrentSessionFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import ua.edu.chnu.awards.auth.security.RetryRequestSessionExpiredStrategy;

/**
 * Tracks the login sessions of the authorization server so a password reset can end them everywhere.
 */
@Configuration
public class LoginSessionConfig {

    private static final int BEFORE_SECURITY_CHAIN = SecurityProperties.DEFAULT_FILTER_ORDER - 1;

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
}
