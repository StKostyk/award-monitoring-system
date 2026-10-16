package ua.edu.chnu.awards.gdpr.dto;

import java.time.Instant;

import ua.edu.chnu.awards.award.dto.UserRef;

/**
 * A legal hold on an account.
 *
 * @param id         identifier
 * @param reason     why it was placed
 * @param placedAt   when it was placed
 * @param placedBy   who placed it
 * @param liftedAt   when it was lifted, null while active
 * @param liftedBy   who lifted it, null while active
 * @param liftReason why it was lifted, null while active
 */
public record LegalHold(long id, String reason, Instant placedAt, UserRef placedBy, Instant liftedAt,
                        UserRef liftedBy, String liftReason) {

    /**
     * Whether the hold still blocks erasure.
     *
     * @return true while not lifted
     */
    public boolean active() {
        return liftedAt == null;
    }
}
