package ua.edu.chnu.awards.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.auth.entity.OneTimeToken;
import ua.edu.chnu.awards.auth.entity.TokenPurpose;
import ua.edu.chnu.awards.auth.entity.UserDevice;
import ua.edu.chnu.awards.auth.event.EmailRestored;
import ua.edu.chnu.awards.auth.event.NewDeviceSignedIn;
import ua.edu.chnu.awards.auth.event.PasswordResetRequested;
import ua.edu.chnu.awards.auth.repository.UserDeviceRepository;
import ua.edu.chnu.awards.auth.security.AuthorizationRevoker;
import ua.edu.chnu.awards.common.event.AfterCommit;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.ClientRequest;
import ua.edu.chnu.awards.config.AuthProperties;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

class DeviceServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");
    private static final String CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final ClientRequest CLIENT = new ClientRequest("203.0.113.7", CHROME, "uk-UA", null);

    private final UserDeviceRepository devices = mock(UserDeviceRepository.class);
    private final OneTimeTokenService tokens = mock(OneTimeTokenService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final AfterCommit afterCommit = mock(AfterCommit.class);
    private final AuthorizationRevoker authorizations = mock(AuthorizationRevoker.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuditService audit = mock(AuditService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AuthProperties properties = new AuthProperties("http://localhost:8080", "http://localhost:4200",
        List.of(), List.of("chnu.edu.ua"), Duration.ofHours(24), Duration.ofHours(1), Duration.ofHours(24),
        Duration.ofHours(1), Duration.ofMinutes(1),
        new AuthProperties.Client("award-web", List.of(), List.of(), Duration.ofMinutes(15),
        Duration.ofDays(7)), new AuthProperties.Jwk("", "", "", ""));
    private final DeviceService service = new DeviceService(devices, new DeviceFingerprint(), tokens, events,
        afterCommit, authorizations, passwordEncoder, audit, properties, users, Clock.fixed(NOW, ZoneOffset.UTC));
    private final User olena = User.builder().id(7L).emailAddress("olena@chnu.edu.ua").firstName("Олена")
        .passwordHash("$2a$12$old").build();

    @Test
    void ac51_anUnknownBrowserIsStoredAndAnnouncedWithARevokeLink() {
        when(devices.findByUserIdAndFingerprint(eq(7L), anyString())).thenReturn(Optional.empty());
        when(tokens.issue(olena, TokenPurpose.SECURITY_REVOKE, Duration.ofHours(24))).thenReturn("raw-token");

        service.recordSignIn(olena, CLIENT);

        ArgumentCaptor<UserDevice> saved = ArgumentCaptor.forClass(UserDevice.class);
        verify(devices).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(olena);
        assertThat(saved.getValue().getBrowser()).isEqualTo("Chrome");
        assertThat(saved.getValue().getOperatingSystem()).isEqualTo("Windows");
        assertThat(saved.getValue().getLastIpAddress()).isEqualTo("203.0.113.7");
        assertThat(saved.getValue().getFingerprint()).hasSize(64);
        assertThat(saved.getValue().getLastUsedAt()).isEqualTo(NOW);
        ArgumentCaptor<NewDeviceSignedIn> event = ArgumentCaptor.forClass(NewDeviceSignedIn.class);
        verify(afterCommit).publish(event.capture());
        assertThat(event.getValue()).isEqualTo(new NewDeviceSignedIn("olena@chnu.edu.ua", "Олена", "Chrome",
            "Windows", "203.0.113.7", NOW, "http://localhost:4200/security/not-me?token=raw-token"));
    }

    @Test
    void ac52_aKnownBrowserOnlyRefreshesAddressAndTimeWithoutAnEmail() {
        UserDevice known = UserDevice.builder().id(3L).user(olena).fingerprint("f").browser("Chrome")
            .operatingSystem("Windows").lastIpAddress("198.51.100.1").lastUsedAt(NOW.minusSeconds(3600)).build();
        when(devices.findByUserIdAndFingerprint(eq(7L), anyString())).thenReturn(Optional.of(known));

        service.recordSignIn(olena, CLIENT);

        assertThat(known.getLastIpAddress()).isEqualTo("203.0.113.7");
        assertThat(known.getLastUsedAt()).isEqualTo(NOW);
        verify(devices, never()).save(any());
        verify(events, never()).publishEvent(any());
        verify(afterCommit, never()).publish(any());
        verify(tokens, never()).issue(any(), any(), any());
    }

    @Test
    void ac53_revokingSignsOutEverywhereForgetsDevicesAndForcesAPasswordReset() {
        when(tokens.redeem("raw", TokenPurpose.SECURITY_REVOKE)).thenReturn(Optional.of(revokeToken(null)));
        when(tokens.issue(olena, TokenPurpose.PASSWORD_RESET, Duration.ofHours(1))).thenReturn("reset-token");
        when(authorizations.revokeAll(olena)).thenReturn(2);
        when(devices.deleteByUserId(7L)).thenReturn(3);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$random");

        service.revoke("raw");

        assertThat(olena.getPasswordHash()).isEqualTo("$2a$12$random");
        verify(tokens).invalidateUnbound(olena, TokenPurpose.SECURITY_REVOKE);
        verify(tokens, never()).invalidate(olena, TokenPurpose.SECURITY_REVOKE);
        verify(tokens).invalidate(olena, TokenPurpose.EMAIL_CHANGE);
        verify(authorizations).revokeAll(olena);
        verify(devices).deleteByUserId(7L);
        verify(afterCommit).publish(new PasswordResetRequested("olena@chnu.edu.ua", "Олена",
            "http://localhost:4200/reset-password?token=reset-token"));
        verify(audit).record(AuditAction.SECURITY_REVOKE, 7L, Map.of("authorizations", 2, "devices", 3));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void ac5_aLinkBoundToThePreviousAddressMovesTheAccountBackBeforeRevoking() {
        when(tokens.redeem("raw", TokenPurpose.SECURITY_REVOKE))
            .thenReturn(Optional.of(revokeToken("olena.old@chnu.edu.ua")));
        when(tokens.issue(olena, TokenPurpose.PASSWORD_RESET, Duration.ofHours(1))).thenReturn("reset-token");
        when(authorizations.revokeAll(7L, "olena@chnu.edu.ua")).thenReturn(1);
        when(authorizations.revokeAll(olena)).thenReturn(0);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$random");

        service.revoke("raw");

        assertThat(olena.getEmailAddress()).isEqualTo("olena.old@chnu.edu.ua");
        verify(users).saveAndFlush(olena);
        verify(tokens).invalidate(olena, TokenPurpose.SECURITY_REVOKE);
        verify(authorizations).revokeAll(7L, "olena@chnu.edu.ua");
        verify(events).publishEvent(new EmailRestored("olena@chnu.edu.ua", "olena.old@chnu.edu.ua", "Олена"));
        verify(afterCommit).publish(new PasswordResetRequested("olena.old@chnu.edu.ua", "Олена",
            "http://localhost:4200/reset-password?token=reset-token"));
        verify(audit).record(AuditAction.SECURITY_REVOKE, 7L, Map.of("authorizations", 1, "devices", 0,
            "restoredEmail", "olena.old@chnu.edu.ua", "replacedEmail", "olena@chnu.edu.ua"));
    }

    @Test
    void ac5_aPreviousAddressTakenMeanwhileIsNotRestoredAndNoResetLinkGoesToTheCurrentOne() {
        when(tokens.redeem("raw", TokenPurpose.SECURITY_REVOKE))
            .thenReturn(Optional.of(revokeToken("olena.old@chnu.edu.ua")));
        when(users.existsByEmailAddressIgnoreCase("olena.old@chnu.edu.ua")).thenReturn(true);
        when(authorizations.revokeAll(olena)).thenReturn(1);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$random");

        service.revoke("raw");

        assertThat(olena.getEmailAddress()).isEqualTo("olena@chnu.edu.ua");
        assertThat(olena.getPasswordHash()).isEqualTo("$2a$12$random");
        verify(users, never()).saveAndFlush(any());
        verify(authorizations).revokeAll(olena);
        verify(authorizations, never()).revokeAll(any(), any());
        verify(tokens, never()).issue(any(), any(), any());
        verify(events, never()).publishEvent(any());
        verify(afterCommit, never()).publish(any());
        verify(audit).record(AuditAction.SECURITY_REVOKE, 7L, Map.of("authorizations", 1, "devices", 0,
            "restoreRefused", "olena.old@chnu.edu.ua"));
    }

    @Test
    void ac5_aRegistrationTakingThePreviousAddressAtTheSameMomentAnswers409() {
        when(tokens.redeem("raw", TokenPurpose.SECURITY_REVOKE))
            .thenReturn(Optional.of(revokeToken("olena.old@chnu.edu.ua")));
        when(users.saveAndFlush(olena)).thenThrow(new DataIntegrityViolationException("uk_users_email"));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$random");

        assertThatThrownBy(() -> service.revoke("raw")).isInstanceOfSatisfying(ApiProblemException.class,
            problem -> assertThat(problem.getType()).isEqualTo("email-taken"));
        verify(events, never()).publishEvent(any());
        verify(afterCommit, never()).publish(any());
    }

    private OneTimeToken revokeToken(String address) {
        return OneTimeToken.builder().user(olena).purpose(TokenPurpose.SECURITY_REVOKE).newEmailAddress(address)
            .build();
    }

    @Test
    void ac53_anUnknownOrUsedTokenIsRefusedWithoutSideEffects() {
        when(tokens.redeem("stale", TokenPurpose.SECURITY_REVOKE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke("stale"))
            .isInstanceOf(ApiProblemException.class)
            .hasMessageContaining("invalid");

        verify(authorizations, never()).revokeAll(any());
        verify(tokens, never()).invalidate(any(), any());
        verify(devices, never()).deleteByUserId(any());
        verify(events, never()).publishEvent(any());
        verify(afterCommit, never()).publish(any());
    }
}
