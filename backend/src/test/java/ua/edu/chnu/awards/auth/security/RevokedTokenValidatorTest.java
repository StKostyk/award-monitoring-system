package ua.edu.chnu.awards.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.oauth2.jwt.Jwt;

class RevokedTokenValidatorTest {

    private static final Instant REVOKED_AT = Instant.parse("2026-09-21T10:00:00Z");

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final RevokedTokenValidator validator = new RevokedTokenValidator(redis);

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void ac65_tokenIssuedBeforeOrWithinTheSecondOfTheRevocationIsRefused() {
        when(values.get("auth:nbf:7")).thenReturn(String.valueOf(REVOKED_AT.getEpochSecond()));

        assertThat(validator.validate(token(REVOKED_AT.minusSeconds(1))).hasErrors()).isTrue();
        assertThat(validator.validate(token(REVOKED_AT)).hasErrors()).isTrue();
    }

    @Test
    void ac65_tokenIssuedAfterTheRevocationIsAccepted() {
        when(values.get("auth:nbf:7")).thenReturn(String.valueOf(REVOKED_AT.getEpochSecond()));

        assertThat(validator.validate(token(REVOKED_AT.plusSeconds(1))).hasErrors()).isFalse();
        assertThat(validator.validate(token(REVOKED_AT.plusSeconds(30))).hasErrors()).isFalse();
    }

    @Test
    void ac65_noRevocationMeansNoCheck() {
        when(values.get("auth:nbf:7")).thenReturn(null);

        assertThat(validator.validate(token(REVOKED_AT.minusSeconds(600))).hasErrors()).isFalse();
    }

    @Test
    void ac65_failsOpenWhenRedisIsDown() {
        when(values.get("auth:nbf:7")).thenThrow(new QueryTimeoutException("down"));

        assertThat(validator.validate(token(REVOKED_AT.minusSeconds(600))).hasErrors()).isFalse();
    }

    private static Jwt token(Instant issuedAt) {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject("7")
            .issuedAt(issuedAt).expiresAt(issuedAt.plusSeconds(900)).build();
    }
}
