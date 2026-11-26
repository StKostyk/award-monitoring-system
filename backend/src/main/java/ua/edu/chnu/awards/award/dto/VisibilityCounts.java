package ua.edu.chnu.awards.award.dto;

/**
 * Approved personal awards of a person by the owner's visibility choice.
 *
 * @param publicAwards     shown on the public page too
 * @param universityAwards shown to signed-in colleagues
 * @param privateAwards    shown to nobody else
 * @param withDocument     those with at least one document, whatever their visibility
 */
public record VisibilityCounts(long publicAwards, long universityAwards, long privateAwards, long withDocument) {
}
