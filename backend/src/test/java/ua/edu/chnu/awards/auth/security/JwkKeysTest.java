package ua.edu.chnu.awards.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.nimbusds.jose.jwk.RSAKey;

import ua.edu.chnu.awards.config.AuthProperties;

class JwkKeysTest {

    @Test
    void ac14_generatesAKeyWhenNoneIsConfigured() {
        JwkKeys keys = new JwkKeys(properties(new AuthProperties.Jwk("", "", "")), new MockEnvironment());

        RSAKey key = keys.rsaKey();
        assertThat(key.getKeyID()).isNotBlank();
        assertThat(key.isPrivate()).isTrue();
        assertThat(key.size()).isEqualTo(2048);
    }

    @Test
    void ac14_loadsAConfiguredPemPairWithItsKeyId() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        String privatePem = "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder().encodeToString(pair.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----";
        String publicPem = "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder().encodeToString(pair.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----";

        JwkKeys keys = new JwkKeys(properties(new AuthProperties.Jwk("key-2026", privatePem, publicPem)),
            new MockEnvironment());

        assertThat(keys.rsaKey().getKeyID()).isEqualTo("key-2026");
        assertThat(keys.rsaKey().toRSAPublicKey()).isEqualTo(pair.getPublic());
    }

    @Test
    void rejectsGarbagePem() {
        assertThatThrownBy(() -> new JwkKeys(properties(new AuthProperties.Jwk("k", "AAAA", "AAAA")),
            new MockEnvironment()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void productionRefusesToStartWithoutAConfiguredKey() {
        MockEnvironment production = new MockEnvironment();
        production.setActiveProfiles("production");

        assertThatThrownBy(() -> new JwkKeys(properties(new AuthProperties.Jwk("", "", "")), production))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("AUTH_JWK_PRIVATE_KEY");
    }

    private static AuthProperties properties(AuthProperties.Jwk jwk) {
        return new AuthProperties("http://localhost:8080", "http://localhost:4200", List.of("http://localhost:4200"),
            List.of("chnu.edu.ua"), Duration.ofHours(24), Duration.ofHours(1), Duration.ofMinutes(1),
            new AuthProperties.Client("award-web", List.of("http://localhost:4200/callback"),
                List.of("http://localhost:4200"), Duration.ofMinutes(15), Duration.ofDays(7)),
            jwk);
    }
}
