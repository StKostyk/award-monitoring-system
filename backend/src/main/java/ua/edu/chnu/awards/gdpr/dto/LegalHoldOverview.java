package ua.edu.chnu.awards.gdpr.dto;

import java.time.Instant;
import java.util.List;

/**
 * The legal holds of an account and its pending erasure.
 *
 * @param holds               every hold, newest first
 * @param erasureScheduledFor when a pending erasure is due, null without one
 */
public record LegalHoldOverview(List<LegalHold> holds, Instant erasureScheduledFor) {

    /**
     * Keeps an unmodifiable copy of the holds.
     */
    public LegalHoldOverview {
        holds = List.copyOf(holds);
    }
}
