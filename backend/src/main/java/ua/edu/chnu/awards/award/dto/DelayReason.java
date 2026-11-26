package ua.edu.chnu.awards.award.dto;

/**
 * Why a request is not moving as expected.
 */
public enum DelayReason {
    /** Nobody holds the reviewing role of the current level for the award's organisation. */
    NO_REVIEWER,
    /** The current level is past its deadline. */
    REVIEW_OVERDUE
}
