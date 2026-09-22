package ua.edu.chnu.awards.config;

import static org.springframework.security.config.Customizer.withDefaults;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import ua.edu.chnu.awards.auth.security.AccessTokenDecoder;
import ua.edu.chnu.awards.auth.security.JwtAuthorityConverter;
import ua.edu.chnu.awards.auth.security.LoginAccessDeniedHandler;
import ua.edu.chnu.awards.auth.security.LoginFailureHandler;
import ua.edu.chnu.awards.auth.security.ProblemDetailsEntryPoint;
import ua.edu.chnu.awards.auth.security.RetryRequestSessionExpiredStrategy;
import ua.edu.chnu.awards.authz.ProblemDetailsAccessDeniedHandler;

/**
 * Resource-server protection for the API and form login for the authorization server.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(AuthProperties.class)
@SuppressWarnings("PMD.SignatureDeclareThrowsException")
public class SecurityConfig {

    private static final int API_ORDER = 2;
    private static final int UNLIMITED_SESSIONS = -1;
    private static final int LOGIN_ORDER = 3;
    private static final String ROLE_SYSTEM_ADMIN = "SYSTEM_ADMIN";

    @Bean
    @Order(API_ORDER)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, JwtAuthorityConverter authorityConverter,
                                               AccessTokenDecoder accessTokenDecoder,
                                               ProblemDetailsEntryPoint entryPoint,
                                               ProblemDetailsAccessDeniedHandler accessDeniedHandler)
            throws Exception {
        http
            .securityMatcher("/api/**", "/actuator/**")
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/verify-email",
                    "/api/v1/auth/resend-verification", "/api/v1/auth/password-reset/request",
                    "/api/v1/auth/password-reset/confirm", "/api/v1/auth/security/revoke").permitAll()
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
    @Order(LOGIN_ORDER)
    SecurityFilterChain loginSecurityFilterChain(HttpSecurity http, DaoAuthenticationProvider authenticationProvider,
                                                 LoginFailureHandler failureHandler,
                                                 LoginAccessDeniedHandler accessDeniedHandler,
                                                 SessionRegistry sessionRegistry, RequestCache requestCache,
                                                 AuthProperties properties) throws Exception {
        http
            .sessionManagement(session -> session
                .maximumSessions(UNLIMITED_SESSIONS)
                .sessionRegistry(sessionRegistry)
                .expiredSessionStrategy(new RetryRequestSessionExpiredStrategy()))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/", "/login", "/error", "/css/**", "/img/**").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().authenticated())
            .authenticationProvider(authenticationProvider)
            .requestCache(cache -> cache.requestCache(requestCache))
            .exceptionHandling(handling -> handling.accessDeniedHandler(accessDeniedHandler))
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl(properties.frontendUrl())
                .failureHandler(failureHandler))
            .logout(withDefaults());
        return http.build();
    }

    /** Publishes denials so they can be audited in one place. */
    @Bean
    AuthorizationEventPublisher authorizationEventPublisher(ApplicationEventPublisher publisher) {
        return new SpringAuthorizationEventPublisher(publisher);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AuthProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Accept-Language"));
        configuration.setExposedHeaders(List.of("Location", "Retry-After"));
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
