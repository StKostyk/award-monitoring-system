package ua.edu.chnu.awards.auth.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.util.Base64;
import java.util.UUID;

import org.springframework.core.env.Environment;
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

    public JwkKeys(AuthProperties properties, Environment environment) {
        AuthProperties.Jwk jwk = properties.jwk();
        if (jwk.privateKey().isBlank() || jwk.publicKey().isBlank()) {
            if (environment.matchesProfiles("production")) {
                throw new IllegalStateException(
                    "AUTH_JWK_PRIVATE_KEY and AUTH_JWK_PUBLIC_KEY are required in production");
            }
            this.rsaKey = jwk.devKeyFile().isBlank() ? generated() : kept(Path.of(jwk.devKeyFile()));
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

    private static void ownerOnly(Path file) throws IOException {
        if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        }
    }

    private static RSAKey generated() {
        log.warn("No signing key configured; generating a key that will not survive a restart");
        return generate();
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

    /**
     * The development key kept in a file: read when present, otherwise generated and written there.
     *
     * @param file where the key is kept
     * @return the key
     */
    static RSAKey kept(Path file) {
        try {
            if (Files.exists(file)) {
                RSAKey key = RSAKey.parse(Files.readString(file));
                if (!key.isPrivate() || key.size() < GENERATED_KEY_SIZE) {
                    throw new IllegalStateException("Development signing key file " + file
                        + " must hold a private RSA key of at least " + GENERATED_KEY_SIZE + " bits");
                }
                return key;
            }
            RSAKey key = generate();
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, key.toJSONString());
            ownerOnly(file);
            log.warn("No signing key configured; generated a development key kept in {}", file);
            return key;
        } catch (IOException | ParseException e) {
            throw new IllegalStateException("Development signing key file " + file + " is unusable", e);
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
