package ua.edu.chnu.awards.award.dto;

/**
 * One award of a batch decision.
 *
 * @param awardId        the award
 * @param requestVersion the version of its request the caller last read
 */
public record BatchItem(Long awardId, Long requestVersion) {
}
