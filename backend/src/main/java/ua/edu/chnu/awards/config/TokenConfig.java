package ua.edu.chnu.awards.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import ua.edu.chnu.awards.auth.security.JwkKeys;
import ua.edu.chnu.awards.auth.security.RotatingRefreshTokenGenerator;

/**
 * Signing keys and token generation.
 */
@Configuration
public class TokenConfig {

    @Bean
    JWKSource<SecurityContext> jwkSource(JwkKeys keys) {
        JWKSet jwkSet = new JWKSet(List.of(keys.rsaKey()));
        return (selector, context) -> selector.select(jwkSet);
    }

    @Bean
    OAuth2TokenGenerator<?> tokenGenerator(JWKSource<SecurityContext> jwkSource,
                                           OAuth2TokenCustomizer<JwtEncodingContext> claimsCustomizer) {
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(jwkSource);
        JwtGenerator jwtGenerator = new JwtGenerator(jwtEncoder);
        jwtGenerator.setJwtCustomizer(claimsCustomizer);
        return new DelegatingOAuth2TokenGenerator(jwtGenerator, new OAuth2AccessTokenGenerator(),
            new RotatingRefreshTokenGenerator());
    }

    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

}
