package ua.edu.chnu.awards.config;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import ua.edu.chnu.awards.auth.security.PublicClientRefreshAuthenticationConverter;
import ua.edu.chnu.awards.auth.security.PublicClientRefreshAuthenticationProvider;
import ua.edu.chnu.awards.auth.security.RefreshTokenReuseGuard;

/**
 * OAuth2 / OpenID Connect provider endpoints backed by the database.
 */
@Configuration
@SuppressWarnings("PMD.SignatureDeclareThrowsException")
public class AuthorizationServerConfig {

    private static final int AUTHORIZATION_SERVER_ORDER = 1;

    @Bean
    @Order(AUTHORIZATION_SERVER_ORDER)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
                                                               OAuth2AuthorizationService authorizationService,
                                                               StringRedisTemplate redisTemplate,
                                                               RegisteredClientRepository registeredClientRepository,
                                                               AuthProperties properties) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer =
            OAuth2AuthorizationServerConfigurer.authorizationServer();
        http
            .securityMatcher(authorizationServer.getEndpointsMatcher())
            .with(authorizationServer, server -> server
                .oidc(withDefaults())
                .clientAuthentication(client -> client
                    .authenticationConverter(new PublicClientRefreshAuthenticationConverter())
                    .authenticationProvider(
                        new PublicClientRefreshAuthenticationProvider(registeredClientRepository)))
                .tokenEndpoint(token -> token.authenticationProviders(providers ->
                    providers.replaceAll(provider -> guardRefreshTokens(provider, authorizationService,
                        redisTemplate, properties)))))
            .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
            .cors(withDefaults())
            .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                new LoginUrlAuthenticationEntryPoint("/login"),
                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        return http.build();
    }

    private static AuthenticationProvider guardRefreshTokens(AuthenticationProvider provider,
                                                             OAuth2AuthorizationService authorizationService,
                                                             StringRedisTemplate redisTemplate,
                                                             AuthProperties properties) {
        if (provider instanceof OAuth2RefreshTokenAuthenticationProvider) {
            return new RefreshTokenReuseGuard(provider, authorizationService, redisTemplate,
                properties.client().refreshTokenTtl());
        }
        return provider;
    }

    @Bean
    RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    @Bean
    OAuth2AuthorizationService authorizationService(JdbcTemplate jdbcTemplate,
                                                    RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
    }

    @Bean
    OAuth2AuthorizationConsentService authorizationConsentService(
        JdbcTemplate jdbcTemplate, RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(AuthProperties properties) {
        return AuthorizationServerSettings.builder().issuer(properties.issuer()).build();
    }
}
