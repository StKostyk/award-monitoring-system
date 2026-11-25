package ua.edu.chnu.awards.user.dto;

/**
 * What the data says about the steps that are never stored.
 *
 * @param noticeAcknowledged true when the person acknowledged the privacy notice
 * @param ownsAward          true when the person owns an award
 * @param gaveDelegation     true when the person ever lent a role
 */
public record OnboardingFacts(boolean noticeAcknowledged, boolean ownsAward, boolean gaveDelegation) {
}
