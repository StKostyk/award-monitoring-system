package ua.edu.chnu.awards.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import ua.edu.chnu.awards.config.AuthProperties;

class RegisteredClientSeederTest {

    private static final AuthProperties.Client SETTINGS = new AuthProperties.Client("award-web",
        List.of("http://localhost:4200/callback", "https://awards.chnu.edu.ua/callback"),
        List.of("http://localhost:4200"), Duration.ofMinutes(15), Duration.ofDays(7));

    @Test
    void ac11_browserClientIsPublicWithPkceAndRotatingRefreshTokens() {
        RegisteredClient client = RegisteredClientSeeder.browserClient(SETTINGS);

        assertThat(client.getId()).isEqualTo("award-web");
        assertThat(client.getClientAuthenticationMethods()).containsExactly(ClientAuthenticationMethod.NONE);
        assertThat(client.getAuthorizationGrantTypes())
            .containsExactlyInAnyOrder(AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.REFRESH_TOKEN);
        assertThat(client.getRedirectUris()).hasSize(2);
        assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(client.getClientSettings().isRequireAuthorizationConsent()).isFalse();
        assertThat(client.getTokenSettings().getAccessTokenTimeToLive()).isEqualTo(Duration.ofMinutes(15));
        assertThat(client.getTokenSettings().getRefreshTokenTimeToLive()).isEqualTo(Duration.ofDays(7));
        assertThat(client.getTokenSettings().isReuseRefreshTokens()).isFalse();
        assertThat(client.getScopes()).containsExactlyInAnyOrder("openid", "profile");
    }

    @Test
    void savesTheClientOnStartup() {
        RegisteredClientRepository repository = mock(RegisteredClientRepository.class);
        AuthProperties properties = new AuthProperties("http://localhost:8080", "http://localhost:4200", List.of(), List.of("chnu.edu.ua"),
            Duration.ofHours(24), Duration.ofMinutes(1), SETTINGS,
            new AuthProperties.Jwk("", "", ""));

        new RegisteredClientSeeder(repository, properties).run(null);

        verify(repository).save(any(RegisteredClient.class));
    }
}
