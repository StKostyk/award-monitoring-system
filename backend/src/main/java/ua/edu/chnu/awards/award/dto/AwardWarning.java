package ua.edu.chnu.awards.award.dto;

import java.util.List;

/**
 * A hint about the award data that does not block saving.
 *
 * @param code    warning code
 * @param field   the field it concerns
 * @param matches own awards that look the same, empty unless the code is {@code POSSIBLE_DUPLICATE}
 */
public record AwardWarning(String code, String field, List<DuplicateMatch> matches) {

    /** The award date lies within the last 30 days. */
    public static final String RECENT_DATE = "RECENT_DATE";

    /** Another award of the owner has the same date and a similar title. */
    public static final String POSSIBLE_DUPLICATE = "POSSIBLE_DUPLICATE";

    /**
     * Keeps an unmodifiable copy of the matches.
     *
     * @param code    warning code
     * @param field   the field it concerns
     * @param matches own awards that look the same
     */
    public AwardWarning {
        matches = List.copyOf(matches);
    }
}
