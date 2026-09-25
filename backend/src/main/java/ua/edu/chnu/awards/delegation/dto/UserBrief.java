package ua.edu.chnu.awards.delegation.dto;

/**
 * The little that is shown about the two people of a delegation.
 *
 * @param id        user identifier
 * @param firstName given name
 * @param lastName  family name
 * @param email     address
 */
public record UserBrief(Long id, String firstName, String lastName, String email) {
}
