package ua.edu.chnu.awards.auth.security;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2DeviceCode;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.OAuth2UserCode;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import ua.edu.chnu.awards.common.HashUtils;

/**
 * Keeps the issued token values of an authorization as SHA-256 hashes: values are hashed before the delegate stores
 * them and presented values are hashed before the delegate looks them up. An authorization found by a token carries
 * the presented value on that token again, so the endpoints that match the token by its value still find it.
 */
public class HashingAuthorizationService implements OAuth2AuthorizationService {

    /**
     * Prefix of a stored token value.
     */
    public static final String PREFIX = "sha256:";

    private static final OAuth2TokenType STATE = new OAuth2TokenType(OAuth2ParameterNames.STATE);
    private static final List<Class<? extends OAuth2Token>> TOKEN_TYPES = List.of(OAuth2AuthorizationCode.class,
        OAuth2AccessToken.class, OidcIdToken.class, OAuth2RefreshToken.class, OAuth2UserCode.class,
        OAuth2DeviceCode.class);

    private final OAuth2AuthorizationService delegate;

    /**
     * Creates the service.
     *
     * @param delegate the store that writes and reads the rows
     */
    public HashingAuthorizationService(OAuth2AuthorizationService delegate) {
        this.delegate = delegate;
    }

    /**
     * Stored form of an issued value.
     *
     * @param value the issued value
     * @return the prefix followed by the lower-case hex SHA-256 of the value
     */
    public static String hash(String value) {
        return PREFIX + HashUtils.sha256Hex(value);
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        delegate.save(withValues(authorization, value -> value.startsWith(PREFIX) ? value : hash(value)));
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        if (STATE.equals(tokenType)) {
            return delegate.findByToken(token, tokenType);
        }
        String stored = hash(token);
        OAuth2Authorization found = delegate.findByToken(stored, tokenType);
        return found == null ? null : withValues(found, value -> value.equals(stored) ? token : value);
    }

    private static OAuth2Authorization withValues(OAuth2Authorization authorization, UnaryOperator<String> values) {
        OAuth2Authorization.Builder builder = OAuth2Authorization.from(authorization);
        for (Class<? extends OAuth2Token> type : TOKEN_TYPES) {
            OAuth2Authorization.Token<? extends OAuth2Token> stored = authorization.getToken(type);
            if (stored != null) {
                String value = stored.getToken().getTokenValue();
                String replacement = values.apply(value);
                if (!replacement.equals(value)) {
                    Map<String, Object> metadata = stored.getMetadata();
                    builder.token(copy(stored.getToken(), replacement), copy -> copy.putAll(metadata));
                }
            }
        }
        return builder.build();
    }

    private static OAuth2Token copy(OAuth2Token token, String value) {
        return switch (token) {
            case OAuth2AuthorizationCode code -> new OAuth2AuthorizationCode(value, code.getIssuedAt(),
                code.getExpiresAt());
            case OAuth2AccessToken access -> new OAuth2AccessToken(access.getTokenType(), value, access.getIssuedAt(),
                access.getExpiresAt(), access.getScopes());
            case OidcIdToken id -> new OidcIdToken(value, id.getIssuedAt(), id.getExpiresAt(), id.getClaims());
            case OAuth2RefreshToken refresh -> new OAuth2RefreshToken(value, refresh.getIssuedAt(),
                refresh.getExpiresAt());
            case OAuth2UserCode user -> new OAuth2UserCode(value, user.getIssuedAt(), user.getExpiresAt());
            case OAuth2DeviceCode device -> new OAuth2DeviceCode(value, device.getIssuedAt(), device.getExpiresAt());
            default -> throw new IllegalArgumentException("Unsupported token type " + token.getClass().getName());
        };
    }
}
