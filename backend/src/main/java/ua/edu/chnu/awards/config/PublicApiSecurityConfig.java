package ua.edu.chnu.awards.config;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * The public API: anonymous reads ahead of every other chain, answering everybody alike.
 */
@Configuration
@SuppressWarnings("PMD.SignatureDeclareThrowsException")
public class PublicApiSecurityConfig {

    private static final int PUBLIC_API_ORDER = 0;

    /**
     * No resource server, so a bearer token, valid or not, is never read; writes fall through to the API chain.
     *
     * @param http the builder
     * @return the chain of {@code GET /api/v1/public/**}
     * @throws Exception when the chain cannot be built
     */
    @Bean
    @Order(PUBLIC_API_ORDER)
    SecurityFilterChain publicApiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/public/**"))
            .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(AbstractHttpConfigurer::disable)
            .csrf(AbstractHttpConfigurer::disable)
            .cors(withDefaults());
        return http.build();
    }
}
