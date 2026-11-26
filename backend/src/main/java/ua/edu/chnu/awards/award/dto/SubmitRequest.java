package ua.edu.chnu.awards.award.dto;

/**
 * Body of a submission.
 *
 * @param version              version of the draft last read
 * @param acknowledgeDuplicate true when the owner confirmed that a possible duplicate is another award
 */
public record SubmitRequest(Long version, Boolean acknowledgeDuplicate) {

    /**
     * Whether the owner confirmed a possible duplicate.
     *
     * @return true only when the flag was sent as true
     */
    public boolean duplicateAcknowledged() {
        return Boolean.TRUE.equals(acknowledgeDuplicate);
    }
}
