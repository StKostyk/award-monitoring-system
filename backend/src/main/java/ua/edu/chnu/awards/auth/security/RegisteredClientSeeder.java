package ua.edu.chnu.awards.auth.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.config.AuthProperties;

import lombok.RequiredArgsConstructor;

/**
 * Registers (or updates) the browser client from configuration when the application starts.
 */
@Component
@RequiredArgsConstructor
public class RegisteredClientSeeder implements ApplicationRunner {

    private final RegisteredClientRepository repository;
    private final AuthProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        repository.save(browserClient(properties.client()));
    }

    static RegisteredClient browserClient(AuthProperties.Client client) {
        RegisteredClient.Builder builder = RegisteredClient.withId(client.id())
            .clientId(client.id())
            .clientName("Award Monitoring web application")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)
            .clientSettings(ClientSettings.builder()
                .requireProofKey(true)
                .requireAuthorizationConsent(false)
                .build())
            .tokenSettings(TokenSettings.builder()
                .accessTokenTimeToLive(client.accessTokenTtl())
                .refreshTokenTimeToLive(client.refreshTokenTtl())
                .reuseRefreshTokens(false)
                .build());
        client.redirectUris().forEach(builder::redirectUri);
        client.postLogoutRedirectUris().forEach(builder::postLogoutRedirectUri);
        return builder.build();
    }
}
