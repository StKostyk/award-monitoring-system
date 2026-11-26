package ua.edu.chnu.awards.award.dto;

/**
 * A colleague a request can be handed over to.
 *
 * @param id        user identifier
 * @param name      first and last name
 * @param email     address
 * @param delegated true when only a delegation lets them review
 */
public record ReviewerCandidate(Long id, String name, String email, boolean delegated) {
}
