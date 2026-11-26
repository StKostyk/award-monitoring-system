package ua.edu.chnu.awards.award.dto;

/**
 * The outcome of joining a person to an account.
 *
 * @param userId      the account that now owns the awards
 * @param movedAwards how many awards moved
 */
public record PersonLinkResult(long userId, int movedAwards) {
}
