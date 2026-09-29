package ua.edu.chnu.awards.config;

import static org.springframework.security.config.Customizer.withDefaults;

import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import ua.edu.chnu.awards.auth.security.PublicClientRefreshAuthenticationConverter;
import ua.edu.chnu.awards.auth.security.PublicClientRefreshAuthenticationProvider;
import ua.edu.chnu.awards.auth.security.RefreshTokenReuseGuard;
import ua.edu.chnu.awards.auth.security.RefusedLogoutHandler;
import ua.edu.chnu.awards.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * OAuth2 / OpenID Connect provider endpoints backed by the database.
 */
@Configuration
@SuppressWarnings("PMD.SignatureDeclareThrowsException")
@RequiredArgsConstructor
public class AuthorizationServerConfig {

    private static final int AUTHORIZATION_SERVER_ORDER = 1;

    private final RefusedLogoutHandler refusedLogout;

    @Bean
    @Order(AUTHORIZATION_SERVER_ORDER)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
                                                               OAuth2AuthorizationService authorizationService,
                                                               UserRepository userRepository,
                                                               StringRedisTemplate redisTemplate,
                                                               RegisteredClientRepository registeredClientRepository,
                                                               AuthorizationServerSettings settings,
                                                               AuthProperties properties) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer =
            OAuth2AuthorizationServerConfigurer.authorizationServer();
        http
            .securityMatcher(authorizationServer.getEndpointsMatcher())
            .with(authorizationServer, server -> server
                .oidc(oidc -> oidc.logoutEndpoint(logout -> logout.errorResponseHandler(refusedLogout)))
                .clientAuthentication(client -> client
                    .authenticationConverter(new PublicClientRefreshAuthenticationConverter(
                        settings.getTokenEndpoint(), settings.getTokenRevocationEndpoint()))
                    .authenticationProvider(
                        new PublicClientRefreshAuthenticationProvider(registeredClientRepository)))
                .tokenEndpoint(token -> token.authenticationProviders(providers ->
                    providers.replaceAll(provider -> guardRefreshTokens(provider, authorizationService,
                        userRepository, redisTemplate, properties)))))
            .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
            .cors(withDefaults())
            .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                new LoginUrlAuthenticationEntryPoint("/login"), browserRequests()));
        return http.build();
    }

    private static MediaTypeRequestMatcher browserRequests() {
        MediaTypeRequestMatcher matcher = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        matcher.setIgnoredMediaTypes(Set.of(MediaType.ALL));
        return matcher;
    }

    private static AuthenticationProvider guardRefreshTokens(AuthenticationProvider provider,
                                                             OAuth2AuthorizationService authorizationService,
                                                             UserRepository userRepository,
                                                             StringRedisTemplate redisTemplate,
                                                             AuthProperties properties) {
        if (provider instanceof OAuth2RefreshTokenAuthenticationProvider) {
            return new RefreshTokenReuseGuard(provider, authorizationService, userRepository, redisTemplate,
                properties.client().refreshTokenTtl());
        }
        return provider;
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(AuthProperties properties) {
        return AuthorizationServerSettings.builder().issuer(properties.issuer()).build();
    }
}
