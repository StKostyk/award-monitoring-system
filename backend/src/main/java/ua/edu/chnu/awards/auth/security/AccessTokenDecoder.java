package ua.edu.chnu.awards.auth.security;

import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

import ua.edu.chnu.awards.config.AuthProperties;

/**
 * Decoder for bearer tokens presented to the API: signature, timestamps, issuer, the {@code token_use} claim (so
 * that id tokens signed with the same key are not accepted as access tokens) and the last sign-out-everywhere
 * of the user.
 */
@Component
public final class AccessTokenDecoder {

    private final JwtDecoder decoder;

    public AccessTokenDecoder(JWKSource<SecurityContext> jwkSource, AuthProperties properties,
                              StringRedisTemplate redis) {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
        processor.setJWTClaimsSetVerifier((claims, context) -> { });
        NimbusJwtDecoder nimbus = new NimbusJwtDecoder(processor);
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(
            JwtValidators.createDefaultWithIssuer(properties.issuer()), accessTokenOnly(),
            new RevokedTokenValidator(redis))));
        this.decoder = nimbus;
    }

    /**
     * The configured decoder.
     *
     * @return decoder for the resource server
     */
    public JwtDecoder decoder() {
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> accessTokenOnly() {
        OAuth2Error error = new OAuth2Error("invalid_token", "Not an access token", null);
        return jwt -> TokenClaimsCustomizer.TOKEN_USE_ACCESS.equals(
            jwt.getClaimAsString(TokenClaimsCustomizer.CLAIM_TOKEN_USE))
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(error);
    }
}
