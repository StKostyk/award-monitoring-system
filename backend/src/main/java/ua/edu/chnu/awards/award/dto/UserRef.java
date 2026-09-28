package ua.edu.chnu.awards.award.dto;

/**
 * The owner of an award.
 *
 * @param id    user identifier
 * @param name  first and last name
 * @param email address
 */
public record UserRef(Long id, String name, String email) {
}
