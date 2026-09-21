package ua.edu.chnu.awards.auth.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.auth.entity.TokenPurpose;
import ua.edu.chnu.awards.auth.entity.UserDevice;
import ua.edu.chnu.awards.auth.event.NewDeviceSignedIn;
import ua.edu.chnu.awards.auth.event.PasswordResetRequested;
import ua.edu.chnu.awards.auth.repository.UserDeviceRepository;
import ua.edu.chnu.awards.auth.security.AuthorizationRevoker;
import ua.edu.chnu.awards.common.web.ClientRequest;
import ua.edu.chnu.awards.config.AuthProperties;
import ua.edu.chnu.awards.user.entity.User;

import lombok.RequiredArgsConstructor;

/**
 * Known browsers per user. A sign-in from an unknown browser is announced by email with a link that, when the
 * owner denies the sign-in, ends every session and forces a new password.
 */
@Service
@RequiredArgsConstructor
public class DeviceService {

    private static final int SECRET_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserDeviceRepository devices;
    private final DeviceFingerprint fingerprints;
    private final OneTimeTokenService tokens;
    private final ApplicationEventPublisher events;
    private final AuthorizationRevoker authorizations;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final AuthProperties properties;
    private final Clock clock;

    /**
     * Refreshes the known device matching the request or stores it as new and publishes {@link NewDeviceSignedIn}.
     * Runs in its own transaction so that a failure here (for instance two simultaneous first sign-ins racing on
     * the unique fingerprint) never rolls back the login itself.
     *
     * @param user   the account that signed in
     * @param client facts of the sign-in request
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSignIn(User user, ClientRequest client) {
        DeviceFingerprint.Device device = fingerprints.of(client.userAgent(), client.acceptLanguage());
        Instant now = clock.instant();
        devices.findByUserIdAndFingerprint(user.getId(), device.fingerprint()).ifPresentOrElse(known -> {
            known.setLastIpAddress(client.ip());
            known.setLastUsedAt(now);
        }, () -> {
            devices.save(UserDevice.builder()
                .user(user)
                .fingerprint(device.fingerprint())
                .browser(device.browser())
                .operatingSystem(device.operatingSystem())
                .lastIpAddress(client.ip())
                .firstSeenAt(now)
                .lastUsedAt(now)
                .build());
            String raw = tokens.issue(user, TokenPurpose.SECURITY_REVOKE, properties.securityRevokeTtl());
            events.publishEvent(new NewDeviceSignedIn(user.getEmailAddress(), user.getFirstName(),
                device.browser(), device.operatingSystem(), client.ip(), now,
                properties.link("/security/not-me", raw)));
        });
    }

    /**
     * The owner denied a sign-in: every authorization and login session ends, the known devices are forgotten,
     * the current password stops working and a reset link is emailed. The account stays active.
     *
     * @param rawToken token from the "this was not me" link
     */
    @Transactional
    public void revoke(String rawToken) {
        User user = tokens.redeemOwner(rawToken, TokenPurpose.SECURITY_REVOKE);
        tokens.invalidate(user, TokenPurpose.SECURITY_REVOKE);
        byte[] secret = new byte[SECRET_BYTES];
        RANDOM.nextBytes(secret);
        user.setPasswordHash(passwordEncoder.encode(Base64.getEncoder().encodeToString(secret)));
        int revoked = authorizations.revokeAll(user.getEmailAddress());
        int forgotten = devices.deleteByUserId(user.getId());
        String raw = tokens.issue(user, TokenPurpose.PASSWORD_RESET, properties.passwordResetTtl());
        events.publishEvent(new PasswordResetRequested(user.getEmailAddress(), user.getFirstName(),
            properties.link("/reset-password", raw)));
        audit.record(AuditAction.SECURITY_REVOKE, user.getId(), Map.of("authorizations", revoked,
            "devices", forgotten));
    }
}
