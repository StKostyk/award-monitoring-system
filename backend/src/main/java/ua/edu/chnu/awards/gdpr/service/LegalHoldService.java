package ua.edu.chnu.awards.gdpr.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.FieldViolation;
import ua.edu.chnu.awards.gdpr.dto.LegalHold;
import ua.edu.chnu.awards.gdpr.dto.LegalHoldOverview;
import ua.edu.chnu.awards.gdpr.repository.LegalHolds;
import ua.edu.chnu.awards.user.service.UserNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Legal holds the GDPR officer places on accounts to keep them from erasure.
 */
@Service
@RequiredArgsConstructor
public class LegalHoldService {

    static final int REASON_MIN = 10;
    static final int REASON_MAX = 500;
    private static final String REASON = "reason";
    private static final String DELETED = "DELETED";

    private final LegalHolds holds;
    private final AuditService audit;
    private final Clock clock;

    /**
     * Every hold of the account.
     *
     * @param userId the account
     * @return the holds, newest first
     * @throws UserNotFoundException when there is no such account
     */
    @Transactional(readOnly = true)
    public LegalHoldOverview overview(long userId) {
        List<LegalHold> list = holds.of(userId);
        if (list.isEmpty() && !holds.accountExists(userId)) {
            throw new UserNotFoundException(userId);
        }
        return new LegalHoldOverview(list, null);
    }

    /**
     * Places a hold on an account that has none.
     *
     * @param userId    the account
     * @param reason    why, 10 to 500 characters after trimming
     * @param officerId the officer placing it
     * @return the new hold
     * @throws ApiProblemException 404 for an unknown account, 409 {@code account-erased} or
     *                             {@code legal-hold-active}, 422 on the reason
     */
    @Transactional
    public LegalHold place(long userId, String reason, long officerId) {
        String text = reason(reason);
        String status = holds.lockAccount(userId).orElseThrow(() -> new UserNotFoundException(userId));
        if (DELETED.equals(status)) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "account-erased", "The account is erased");
        }
        if (holds.isActive(userId)) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "legal-hold-active",
                "The account is already under a legal hold");
        }
        long holdId = holds.place(userId, text, officerId, clock.instant());
        audit.record(AuditAction.LEGAL_HOLD_PLACED, AuditEntityConstants.GDPR, officerId, userId,
            Map.of("holdId", holdId, "reasonLength", text.length()));
        return holds.find(userId, holdId).orElseThrow();
    }

    /**
     * Lifts an active hold.
     *
     * @param userId    the account
     * @param holdId    the hold
     * @param reason    why, 10 to 500 characters after trimming
     * @param officerId the officer lifting it
     * @return the lifted hold
     * @throws ApiProblemException 404 {@code legal-hold-not-found} when the account has no such hold,
     *                             409 {@code legal-hold-lifted}, 422 on the reason
     */
    @Transactional
    public LegalHold lift(long userId, long holdId, String reason, long officerId) {
        String text = reason(reason);
        holds.lockAccount(userId);
        LegalHold hold = holds.find(userId, holdId).orElseThrow(() -> new ApiProblemException(
            HttpStatus.NOT_FOUND, "legal-hold-not-found", "The account has no such legal hold"));
        if (!hold.active() || !holds.lift(holdId, text, officerId, clock.instant())) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "legal-hold-lifted", "The hold is already lifted");
        }
        audit.record(AuditAction.LEGAL_HOLD_LIFTED, AuditEntityConstants.GDPR, officerId, userId,
            Map.of("holdId", holdId, "reasonLength", text.length()));
        return holds.find(userId, holdId).orElseThrow();
    }

    private static String reason(String sent) {
        String reason = sent == null ? "" : sent.strip();
        if (reason.isEmpty()) {
            throw invalidReason("required", "A reason is required");
        }
        if (reason.length() < REASON_MIN) {
            throw invalidReason("too-short", "At least " + REASON_MIN + " characters");
        }
        if (reason.length() > REASON_MAX) {
            throw invalidReason("too-long", "At most " + REASON_MAX + " characters");
        }
        return reason;
    }

    private static ApiProblemException invalidReason(String code, String message) {
        return ApiProblemException.validationFailed("The reason is not valid",
            List.of(new FieldViolation(REASON, code, message)));
    }
}
