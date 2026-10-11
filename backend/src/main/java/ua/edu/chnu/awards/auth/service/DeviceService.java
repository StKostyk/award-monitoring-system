package ua.edu.chnu.awards.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
import ua.edu.chnu.awards.common.SecretUtils;
import ua.edu.chnu.awards.common.event.AfterCommit;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.ClientRequest;
import ua.edu.chnu.awards.config.AuthProperties;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Known browsers per user. A sign-in from an unknown browser is announced by email with a link that, when the
 * owner denies the sign-in, ends every session and forces a new password.
 */
@Service
@RequiredArgsConstructor
public class DeviceService {

    private static final int SECRET_BYTES = 32;

    private final UserDeviceRepository devices;
    private final DeviceFingerprint fingerprints;
    private final OneTimeTokenService tokens;
    private final ApplicationEventPublisher events;
    private final AfterCommit afterCommit;
    private final AuthorizationRevoker authorizations;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final AuthProperties properties;
    private final UserRepository users;
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
            afterCommit.publish(new NewDeviceSignedIn(user.getEmailAddress(), user.getFirstName(),
                device.browser(), device.operatingSystem(), client.ip(), now,
                properties.link("/security/not-me", raw)));
        });
    }

    /**
     * The owner denied a sign-in or an address change: every authorization and login session ends, the known
     * devices are forgotten and the current password stops working. A link bound to a previous address first
     * moves the account back to it and tells the address it left; links of a new device never cancel links bound
     * to a previous address. The reset link goes to the sign-in address, except when a bound address could not be
     * restored because another account uses it: then nobody gets one, since the current address may be the
     * intruder's, and the administrator has to help. The account stays active.
     *
     * @param rawToken token from the "this was not me" link
     * @throws ApiProblemException 410 {@code token-invalid}; 409 {@code email-taken} when another account takes
     *                             the previous address at the same moment (nothing changes, the link stays usable)
     */
    @Transactional
    public void revoke(String rawToken) {
        OneTimeToken token = tokens.redeem(rawToken, TokenPurpose.SECURITY_REVOKE)
            .orElseThrow(OneTimeTokenService::gone);
        User user = token.getUser();
        String bound = token.getNewEmailAddress();
        if (bound == null) {
            tokens.invalidateUnbound(user, TokenPurpose.SECURITY_REVOKE);
        } else {
            tokens.invalidate(user, TokenPurpose.SECURITY_REVOKE);
        }
        tokens.invalidate(user, TokenPurpose.EMAIL_CHANGE);
        user.setPasswordHash(passwordEncoder.encode(SecretUtils.randomSecret(SECRET_BYTES)));
        final String current = user.getEmailAddress();
        final Restore outcome = restore(user, bound);
        int revoked = outcome == Restore.RESTORED ? authorizations.revokeAll(user.getId(), current) : 0;
        revoked += authorizations.revokeAll(user);
        int forgotten = devices.deleteByUserId(user.getId());
        Map<String, Object> details = new HashMap<>(Map.of("authorizations", revoked, "devices", forgotten));
        if (outcome == Restore.REFUSED) {
            details.put("restoreRefused", bound);
        } else {
            String raw = tokens.issue(user, TokenPurpose.PASSWORD_RESET, properties.passwordResetTtl());
            afterCommit.publish(new PasswordResetRequested(user.getEmailAddress(), user.getFirstName(),
                properties.link("/reset-password", raw)));
        }
        if (outcome == Restore.RESTORED) {
            details.put("restoredEmail", user.getEmailAddress());
            details.put("replacedEmail", current);
            events.publishEvent(new EmailRestored(current, user.getEmailAddress(), user.getFirstName()));
        }
        audit.record(AuditAction.SECURITY_REVOKE, user.getId(), details);
    }

    private Restore restore(User user, String address) {
        if (address == null || address.equalsIgnoreCase(user.getEmailAddress())) {
            return Restore.NONE;
        }
        if (users.existsByEmailAddressIgnoreCase(address)) {
            return Restore.REFUSED;
        }
        user.setEmailAddress(address);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "email-taken",
                "The previous address was just taken by another account; open the link again", e);
        }
        return Restore.RESTORED;
    }

    private enum Restore { NONE, RESTORED, REFUSED }
}
