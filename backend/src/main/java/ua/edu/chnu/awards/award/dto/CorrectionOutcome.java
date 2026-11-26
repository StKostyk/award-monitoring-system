package ua.edu.chnu.awards.award.dto;

import java.util.List;

/**
 * Result of a reviewer's correction.
 *
 * @param award          the corrected award with its request
 * @param requestVersion the version of the request afterwards
 * @param changedFields  snapshot fields that changed
 */
public record CorrectionOutcome(AwardResponse award, long requestVersion, List<String> changedFields) {

    /**
     * Keeps an unmodifiable copy of the changed fields.
     */
    public CorrectionOutcome {
        changedFields = List.copyOf(changedFields);
    }
}
