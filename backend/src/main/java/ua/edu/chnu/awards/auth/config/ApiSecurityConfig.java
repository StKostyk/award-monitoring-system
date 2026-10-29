package ua.edu.chnu.awards.auth.config;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import com.fasterxml.jackson.databind.ObjectMapper;

import ua.edu.chnu.awards.auth.security.AccessTokenDecoder;
import ua.edu.chnu.awards.auth.security.ApiUserRateLimitFilter;
import ua.edu.chnu.awards.auth.security.JwtAuthorityConverter;
import ua.edu.chnu.awards.auth.security.ProblemDetailsEntryPoint;
import ua.edu.chnu.awards.authz.ProblemDetailsAccessDeniedHandler;
import ua.edu.chnu.awards.common.limit.FixedWindowCounter;
import ua.edu.chnu.awards.common.limit.RateLimitMetrics;
import ua.edu.chnu.awards.common.limit.RateLimitProperties;

/**
 * Resource-server protection for the API, with the per-user request limits after the bearer token filter.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
@SuppressWarnings("PMD.SignatureDeclareThrowsException")
public class ApiSecurityConfig {

    private static final int API_ORDER = 2;
    private static final String ROLE_SYSTEM_ADMIN = "SYSTEM_ADMIN";

    @Bean
    @Order(API_ORDER)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, JwtAuthorityConverter authorityConverter,
                                               AccessTokenDecoder accessTokenDecoder,
                                               ProblemDetailsEntryPoint entryPoint,
                                               ProblemDetailsAccessDeniedHandler accessDeniedHandler,
                                               ApiUserRateLimitFilter apiUserRateLimit)
            throws Exception {
        http
            .securityMatcher("/api/**", "/actuator/**")
            .addFilterAfter(apiUserRateLimit, BearerTokenAuthenticationFilter.class)
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/verify-email",
                    "/api/v1/auth/resend-verification", "/api/v1/auth/password-reset/request",
                    "/api/v1/auth/password-reset/confirm", "/api/v1/auth/security/revoke",
                    "/api/v1/auth/email-change/confirm").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/organizations").permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/actuator/**").hasRole(ROLE_SYSTEM_ADMIN)
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.decoder(accessTokenDecoder.decoder()).jwtAuthenticationConverter(authorityConverter))
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(accessDeniedHandler))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(AbstractHttpConfigurer::disable)
            .cors(withDefaults());
        return http.build();
    }

    @Bean
    ApiUserRateLimitFilter apiUserRateLimitFilter(FixedWindowCounter counter, RateLimitMetrics metrics,
                                                  RateLimitProperties properties, ObjectMapper objectMapper) {
        return new ApiUserRateLimitFilter(counter, metrics, properties, objectMapper);
    }

    /**
     * Keeps the per-user limit out of the servlet filters; it runs only inside the API chain.
     *
     * @param filter the per-user limit
     * @return the disabled registration
     */
    @Bean
    FilterRegistrationBean<ApiUserRateLimitFilter> apiUserRateLimitFilterRegistration(
            ApiUserRateLimitFilter filter) {
        FilterRegistrationBean<ApiUserRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
