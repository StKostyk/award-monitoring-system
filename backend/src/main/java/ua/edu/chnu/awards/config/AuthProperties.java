package ua.edu.chnu.awards.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the authorization server: issuer, the browser client and signing keys.
 *
 * @param issuer         issuer URL placed in tokens and the discovery document
 * @param allowedOrigins origins allowed to call the token, discovery and API endpoints from a browser
 * @param client         the single public browser client
 * @param jwk            signing key material; generated at start-up when absent
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
    @DefaultValue("http://localhost:8080") String issuer,
    @DefaultValue("http://localhost:4200") List<String> allowedOrigins,
    @DefaultValue Client client,
    @DefaultValue Jwk jwk) {

    /**
     * Registered browser client.
     *
     * @param id                     OAuth2 client id
     * @param redirectUris           allowed redirect URIs after login
     * @param postLogoutRedirectUris allowed redirect URIs after logout
     * @param accessTokenTtl         lifetime of access tokens
     * @param refreshTokenTtl        lifetime of refresh tokens
     */
    public record Client(
        @DefaultValue("award-web") String id,
        @DefaultValue("http://localhost:4200/callback") List<String> redirectUris,
        @DefaultValue("http://localhost:4200") List<String> postLogoutRedirectUris,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("7d") Duration refreshTokenTtl) {
    }

    /**
     * RSA key pair in PEM form. Both parts empty means a key is generated on every start.
     *
     * @param keyId      the {@code kid} header value
     * @param privateKey PKCS#8 PEM
     * @param publicKey  X.509 PEM
     */
    public record Jwk(
        @DefaultValue("") String keyId,
        @DefaultValue("") String privateKey,
        @DefaultValue("") String publicKey) {
    }
}
