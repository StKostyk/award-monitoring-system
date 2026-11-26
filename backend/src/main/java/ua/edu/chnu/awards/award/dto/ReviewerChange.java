package ua.edu.chnu.awards.award.dto;

/**
 * A claim, take-over or hand-over of a request.
 *
 * @param requestVersion the version of the request the caller last read
 * @param reviewerId     the colleague to hand the request to; null to claim it for the caller
 * @param takeOver       true to take a request over from the reviewer holding it
 */
public record ReviewerChange(Long requestVersion, Long reviewerId, Boolean takeOver) {

    /**
     * Whether the caller asks to take the request over.
     *
     * @return true when {@code takeOver} was sent as true
     */
    public boolean isTakeOver() {
        return Boolean.TRUE.equals(takeOver);
    }
}
