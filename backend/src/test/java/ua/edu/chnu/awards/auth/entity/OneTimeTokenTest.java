package ua.edu.chnu.awards.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

class OneTimeTokenTest {

    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");

    @Test
    void freshTokenIsUsableUntilExpiry() {
        OneTimeToken token = OneTimeToken.builder()
            .purpose(TokenPurpose.EMAIL_VERIFICATION)
            .expiresAt(NOW.plus(1, ChronoUnit.HOURS))
            .build();

        assertThat(token.isUsableAt(NOW)).isTrue();
        assertThat(token.isUsableAt(NOW.plus(2, ChronoUnit.HOURS))).isFalse();
    }

    @Test
    void usedTokenIsNotUsableAgain() {
        OneTimeToken token = OneTimeToken.builder()
            .purpose(TokenPurpose.PASSWORD_RESET)
            .expiresAt(NOW.plus(1, ChronoUnit.HOURS))
            .build();

        token.markUsed(NOW);

        assertThat(token.getUsedAt()).isEqualTo(NOW);
        assertThat(token.isUsableAt(NOW.plusSeconds(1))).isFalse();
    }
}
