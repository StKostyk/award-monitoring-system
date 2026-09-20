package ua.edu.chnu.awards.auth.security;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.nimbusds.jose.jwk.RSAKey;

import ua.edu.chnu.awards.config.AuthProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Provides the RSA key used to sign tokens: loaded from configuration or generated at start-up.
 */
@Component
@Slf4j
public final class JwkKeys {

    private static final int GENERATED_KEY_SIZE = 2048;

    private final RSAKey rsaKey;

    public JwkKeys(AuthProperties properties) {
        AuthProperties.Jwk jwk = properties.jwk();
        if (jwk.privateKey().isBlank() || jwk.publicKey().isBlank()) {
            log.warn("No signing key configured; generating a key that will not survive a restart");
            this.rsaKey = generate();
        } else {
            this.rsaKey = load(jwk);
        }
    }

    /**
     * The signing key with its key id.
     *
     * @return RSA key including the private part
     */
    public RSAKey rsaKey() {
        return rsaKey;
    }

    static RSAKey generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(GENERATED_KEY_SIZE);
            KeyPair pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA is not available", e);
        }
    }

    static RSAKey load(AuthProperties.Jwk jwk) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(
                new X509EncodedKeySpec(decodePem(jwk.publicKey())));
            RSAPrivateKey privateKey = (RSAPrivateKey) factory.generatePrivate(
                new PKCS8EncodedKeySpec(decodePem(jwk.privateKey())));
            String keyId = jwk.keyId().isBlank() ? "key-1" : jwk.keyId();
            return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(keyId).build();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Configured signing key is not a valid RSA PEM pair", e);
        }
    }

    private static byte[] decodePem(String pem) {
        String body = pem.replaceAll("-----(BEGIN|END)[A-Z ]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }
}
