package ua.edu.chnu.awards.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;

class RotatingRefreshTokenGeneratorTest {

    private final RotatingRefreshTokenGenerator generator = new RotatingRefreshTokenGenerator();

    @Test
    void ac12_publicClientReceivesARefreshTokenWithTheConfiguredLifetime() {
        RegisteredClient client = RegisteredClient.withId("award-web").clientId("award-web")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("http://localhost:4200/callback")
            .tokenSettings(TokenSettings.builder().refreshTokenTimeToLive(Duration.ofDays(7)).build())
            .build();

        OAuth2RefreshToken token = generator.generate(DefaultOAuth2TokenContext.builder()
            .registeredClient(client)
            .principal(new TestingAuthenticationToken("u", "p"))
            .tokenType(OAuth2TokenType.REFRESH_TOKEN)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .build());

        assertThat(token).isNotNull();
        assertThat(token.getTokenValue()).hasSizeGreaterThan(100);
        assertThat(Duration.between(token.getIssuedAt(), token.getExpiresAt()).truncatedTo(ChronoUnit.HOURS))
            .isEqualTo(Duration.ofDays(7));
    }

    @Test
    void otherTokenTypesAreNotHandled() {
        RegisteredClient client = RegisteredClient.withId("c").clientId("c")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/cb").build();

        assertThat(generator.generate(DefaultOAuth2TokenContext.builder()
            .registeredClient(client)
            .principal(new TestingAuthenticationToken("u", "p"))
            .tokenType(OAuth2TokenType.ACCESS_TOKEN)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .build())).isNull();
    }
}
