package ua.edu.chnu.awards.analytics.dto;

/**
 * Whether a department rank can be shown.
 */
public enum RankStatus {
    /** The figures are shown. */
    RANKED,
    /** The person has not granted the analytics consent. */
    CONSENT_REQUIRED,
    /** The person's unit is not a department. */
    NO_DEPARTMENT,
    /** The department has fewer active accounts than the minimum. */
    GROUP_TOO_SMALL
}
