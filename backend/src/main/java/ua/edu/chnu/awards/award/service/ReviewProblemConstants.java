package ua.edu.chnu.awards.award.service;

/**
 * Problem type slugs of the review workflow, shared by the services and mirrored in the review screens.
 */
@SuppressWarnings("PMD.DataClass")
public final class ReviewProblemConstants {

    /** The award changed since the caller read it. */
    public static final String AWARD_STALE = "award-stale";
    /** Another reviewer holds the request, or nobody does when the caller should. */
    public static final String REQUEST_CLAIMED = "request-claimed";
    /** The request was already decided. */
    public static final String REQUEST_CLOSED = "request-closed";
    /** The request changed since the caller read it. */
    public static final String REQUEST_STALE = "request-stale";
    /** The request is at the highest level. */
    public static final String NO_HIGHER_LEVEL = "no-higher-level";

    private ReviewProblemConstants() {
    }
}
