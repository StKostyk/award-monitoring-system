package ua.edu.chnu.awards.gdpr.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.auth.service.LoginAttemptService;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.ClientRequest;
import ua.edu.chnu.awards.gdpr.config.ErasureProperties;
import ua.edu.chnu.awards.gdpr.dto.ErasureRequest;
import ua.edu.chnu.awards.gdpr.event.ErasureCancelled;
import ua.edu.chnu.awards.gdpr.event.ErasureRequested;
import ua.edu.chnu.awards.gdpr.event.ErasureWarned;
import ua.edu.chnu.awards.gdpr.repository.ErasureRequests;
import ua.edu.chnu.awards.gdpr.repository.LegalHolds;
import ua.edu.chnu.awards.user.AccountSessions;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.service.UserAccounts;
import ua.edu.chnu.awards.user.service.UserNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Requests of people to erase their account after a grace period, their cancellation and the reminder before the
 * erasure.
 */
@Service
@RequiredArgsConstructor
public class ErasureRequestService {

    private static final String REQUEST_ID = "requestId";

    private final UserAccounts users;
    private final ErasureRequests requests;
    private final LegalHolds holds;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService attempts;
    private final AccountSessions sessions;
    private final ErasureProperties properties;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * Schedules the erasure of the caller's account after the grace period. A wrong password counts as a failed
     * sign-in; the failure that locks the account also signs it out, and that survives the refusal.
     *
     * @param userId   the caller
     * @param password the caller's current password
     * @return the pending request
     * @throws ApiProblemException 423 {@code account-locked}, 403 {@code password-mismatch}, 409
     *                             {@code erasure-pending}, {@code legal-hold} or {@code last-administrator}
     */
    @Transactional(noRollbackFor = ApiProblemException.class)
    public ErasureRequest request(long userId, String password) {
        User user = users.findByIdForUpdate(userId).orElseThrow(() -> new UserNotFoundException(userId));
        requirePassword(user, password);
        requireErasable(userId);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant due = now.plus(properties.gracePeriod());
        long requestId = requests.create(userId, now, due);
        audit.record(AuditAction.ERASURE_REQUESTED, AuditEntityConstants.GDPR, userId, userId,
            Map.of(REQUEST_ID, requestId, "scheduledFor", due.toString()));
        events.publishEvent(new ErasureRequested(user.getEmailAddress(), user.getFirstName(), due));
        return new ErasureRequest(requestId, now, due, false);
    }

    /**
     * Cancels the caller's pending request.
     *
     * @param userId the caller
     * @throws ApiProblemException 409 {@code erasure-not-pending}
     */
    @Transactional
    public void cancel(long userId) {
        User user = users.findByIdForUpdate(userId).orElseThrow(() -> new UserNotFoundException(userId));
        long requestId = requests.cancel(userId, clock.instant()).orElseThrow(() -> new ApiProblemException(
            HttpStatus.CONFLICT, "erasure-not-pending", "No erasure is pending"));
        audit.record(AuditAction.ERASURE_CANCELLED, AuditEntityConstants.GDPR, userId, userId,
            Map.of(REQUEST_ID, requestId));
        events.publishEvent(new ErasureCancelled(user.getEmailAddress(), user.getFirstName()));
    }

    /**
     * Reminds the people whose erasure falls within the warning period, once per request.
     *
     * @param now the current moment
     * @return how many reminders were queued
     */
    @Transactional
    public int warnDue(Instant now) {
        List<ErasureRequests.Due> due = requests.dueForWarning(now, properties.warningBefore());
        for (ErasureRequests.Due request : due) {
            requests.markWarned(request.requestId(), now);
            events.publishEvent(new ErasureWarned(request.email(), request.firstName(), request.scheduledFor()));
        }
        return due.size();
    }

    private void requirePassword(User user, String password) {
        if (attempts.isLocked(user.getEmailAddress())) {
            throw new ApiProblemException(HttpStatus.LOCKED, "account-locked",
                "The account is locked after failed sign-ins; try again later");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            if (attempts.recordFailure(user.getEmailAddress(), ClientRequest.current().ip())) {
                sessions.signOutEverywhere(user);
            }
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "password-mismatch", "The password is wrong");
        }
    }

    private void requireErasable(long userId) {
        if (requests.pending(userId).isPresent()) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "erasure-pending", "An erasure is already pending");
        }
        if (holds.isActive(userId)) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "legal-hold", "The account cannot be erased now");
        }
        if (requests.isOnlyAdministrator(userId, LocalDate.now(clock))) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "last-administrator",
                "The only system administrator cannot be erased; appoint another one first");
        }
    }
}
