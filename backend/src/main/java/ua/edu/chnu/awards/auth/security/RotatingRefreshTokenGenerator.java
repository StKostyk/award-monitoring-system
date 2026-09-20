package ua.edu.chnu.awards.auth.security;

import java.time.Instant;
import java.util.Base64;

import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

/**
 * Issues refresh tokens to every client, including the public browser client. Public clients are allowed
 * refresh tokens because every token is single-use and reuse revokes the authorization.
 */
public final class RotatingRefreshTokenGenerator implements OAuth2TokenGenerator<OAuth2RefreshToken> {

    private static final int TOKEN_BYTES = 96;

    private final StringKeyGenerator keyGenerator =
        new Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), TOKEN_BYTES);

    @Override
    public OAuth2RefreshToken generate(OAuth2TokenContext context) {
        if (!OAuth2TokenType.REFRESH_TOKEN.equals(context.getTokenType())) {
            return null;
        }
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(context.getRegisteredClient().getTokenSettings().getRefreshTokenTimeToLive());
        return new OAuth2RefreshToken(keyGenerator.generateKey(), issuedAt, expiresAt);
    }
}
