package ua.edu.chnu.awards.award.entity;

/**
 * Who sees an approved personal award beyond its owner and the reviewers whose scope covers it.
 */
public enum AwardVisibility {
    /** Nobody else. */
    PRIVATE,
    /** Every signed-in colleague on the achievements page. */
    UNIVERSITY,
    /** Colleagues and the public achievements page. */
    PUBLIC
}
