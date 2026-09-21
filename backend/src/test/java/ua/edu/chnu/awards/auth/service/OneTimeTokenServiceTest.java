package ua.edu.chnu.awards.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.auth.entity.OneTimeToken;
import ua.edu.chnu.awards.auth.entity.TokenPurpose;
import ua.edu.chnu.awards.auth.repository.OneTimeTokenRepository;
import ua.edu.chnu.awards.common.HashUtils;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.user.entity.User;

class OneTimeTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");

    private final OneTimeTokenRepository repository = mock(OneTimeTokenRepository.class);
    private final OneTimeTokenService service = new OneTimeTokenService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void ac21_issuesARandomTokenAndStoresOnlyItsHash() {
        User user = User.builder().id(1L).build();

        String raw = service.issue(user, TokenPurpose.EMAIL_VERIFICATION, Duration.ofHours(24));

        ArgumentCaptor<OneTimeToken> saved = ArgumentCaptor.forClass(OneTimeToken.class);
        verify(repository).save(saved.capture());
        assertThat(raw).hasSizeGreaterThanOrEqualTo(43).doesNotContain("=");
        assertThat(saved.getValue().getTokenHash()).hasSize(64).isNotEqualTo(raw);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(saved.getValue().getPurpose()).isEqualTo(TokenPurpose.EMAIL_VERIFICATION);
        assertThat(service.issue(user, TokenPurpose.EMAIL_VERIFICATION, Duration.ofHours(1))).isNotEqualTo(raw);
    }

    @Test
    void ac53_invalidateCancelsEveryUnusedTokenOfTheUserForThePurpose() {
        User user = User.builder().id(1L).build();
        when(repository.cancelUnused(1L, TokenPurpose.SECURITY_REVOKE, NOW)).thenReturn(2);

        assertThat(service.invalidate(user, TokenPurpose.SECURITY_REVOKE)).isEqualTo(2);
    }

    @Test
    void ac25_ac32_ownerLookupsAnswerGoneForUnusableTokens() {
        User user = User.builder().id(1L).build();
        OneTimeToken token = OneTimeToken.builder().user(user).expiresAt(NOW.plusSeconds(60)).build();
        String hash = HashUtils.sha256Hex("raw");
        when(repository.findByTokenHashAndPurpose(hash, TokenPurpose.PASSWORD_RESET)).thenReturn(Optional.of(token));
        when(repository.redeem(hash, TokenPurpose.PASSWORD_RESET, NOW)).thenReturn(1);

        assertThat(service.peekOwner("raw", TokenPurpose.PASSWORD_RESET)).isSameAs(user);
        assertThat(service.redeemOwner("raw", TokenPurpose.PASSWORD_RESET)).isSameAs(user);
        assertThatThrownBy(() -> service.peekOwner("other", TokenPurpose.PASSWORD_RESET))
            .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.GONE);
                assertThat(e.getType()).isEqualTo("token-invalid");
            });
        assertThatThrownBy(() -> service.redeemOwner("other", TokenPurpose.PASSWORD_RESET))
            .isInstanceOf(ApiProblemException.class);
    }

    @Test
    void ac25_redeemsATokenThroughOneAtomicUpdate() {
        String hash = HashUtils.sha256Hex("raw");
        OneTimeToken token = OneTimeToken.builder().purpose(TokenPurpose.EMAIL_VERIFICATION)
            .expiresAt(NOW.plusSeconds(60)).build();
        when(repository.redeem(hash, TokenPurpose.EMAIL_VERIFICATION, NOW)).thenReturn(1, 0);
        when(repository.findByTokenHashAndPurpose(hash, TokenPurpose.EMAIL_VERIFICATION))
            .thenReturn(Optional.of(token));

        assertThat(service.redeem("raw", TokenPurpose.EMAIL_VERIFICATION)).contains(token);
        assertThat(service.redeem("raw", TokenPurpose.EMAIL_VERIFICATION)).isEmpty();
        verify(repository, times(1)).findByTokenHashAndPurpose(hash, TokenPurpose.EMAIL_VERIFICATION);
    }

    @Test
    void ac25_peekReturnsOnlyUsableTokensWithoutConsumingThem() {
        String hash = HashUtils.sha256Hex("raw");
        OneTimeToken usable = OneTimeToken.builder().purpose(TokenPurpose.EMAIL_VERIFICATION)
            .expiresAt(NOW.plusSeconds(60)).build();
        OneTimeToken used = OneTimeToken.builder().purpose(TokenPurpose.EMAIL_VERIFICATION)
            .expiresAt(NOW.plusSeconds(60)).usedAt(NOW.minusSeconds(1)).build();
        OneTimeToken expired = OneTimeToken.builder().purpose(TokenPurpose.EMAIL_VERIFICATION)
            .expiresAt(NOW.minusSeconds(1)).build();
        when(repository.findByTokenHashAndPurpose(hash, TokenPurpose.EMAIL_VERIFICATION))
            .thenReturn(Optional.of(usable), Optional.of(used), Optional.of(expired), Optional.empty());

        assertThat(service.peek("raw", TokenPurpose.EMAIL_VERIFICATION)).contains(usable);
        assertThat(service.peek("raw", TokenPurpose.EMAIL_VERIFICATION)).isEmpty();
        assertThat(service.peek("raw", TokenPurpose.EMAIL_VERIFICATION)).isEmpty();
        assertThat(service.peek("raw", TokenPurpose.EMAIL_VERIFICATION)).isEmpty();
        verify(repository, never()).redeem(any(), any(), any());
    }

    @Test
    void ac26_expiredOrUnknownTokensAreNotRedeemed() {
        when(repository.redeem(any(), any(), any())).thenReturn(0);

        assertThat(service.redeem("old", TokenPurpose.EMAIL_VERIFICATION)).isEmpty();
        verify(repository, never()).findByTokenHashAndPurpose(any(), any());
    }
}
