package ua.edu.chnu.awards.award.dto;

/**
 * The account a person without an account is joined to.
 *
 * @param userId  the registered account
 * @param version the person's version last read
 */
public record PersonLinkRequest(Long userId, Long version) {
}
