package ua.edu.chnu.awards.gdpr.service;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.gdpr.repository.ErasureRequests;
import ua.edu.chnu.awards.gdpr.repository.LegalHolds;

import lombok.RequiredArgsConstructor;

/**
 * The checks an account passes before it is erased, shared by the request and the run that carries it out.
 */
@Component
@RequiredArgsConstructor
class ErasureGuard {

    private final LegalHolds holds;
    private final ErasureRequests requests;

    /**
     * What keeps the account from erasure on the day, a legal hold first.
     *
     * @param userId the account
     * @param day    the day whose role assignments count
     * @return the blocker, or empty when the account can be erased
     */
    Optional<ErasureBlocker> blocker(long userId, LocalDate day) {
        if (holds.isActive(userId)) {
            return Optional.of(ErasureBlocker.LEGAL_HOLD);
        }
        if (requests.isOnlyAdministrator(userId, day)) {
            return Optional.of(ErasureBlocker.LAST_ADMINISTRATOR);
        }
        return Optional.empty();
    }
}
