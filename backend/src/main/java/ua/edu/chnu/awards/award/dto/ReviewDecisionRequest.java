package ua.edu.chnu.awards.award.dto;

/**
 * A reviewer's decision on the request of an award.
 *
 * @param decision       what the reviewer decides
 * @param requestVersion the version of the request the caller last read
 * @param comment        the reviewer's comment; required to return or reject
 * @param verified       true when the reviewer checked the award's documents on approval
 */
public record ReviewDecisionRequest(Decision decision, Long requestVersion, String comment, Boolean verified) {

    /**
     * Whether the reviewer confirmed the documents.
     *
     * @return true only when {@code verified} is true
     */
    public boolean isVerified() {
        return Boolean.TRUE.equals(verified);
    }

    /**
     * The decisions a reviewer takes.
     */
    public enum Decision {
        APPROVE,
        REJECT,
        RETURN,
        ESCALATE
    }
}
