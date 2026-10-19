package ua.edu.chnu.awards.award.dto;

/**
 * Personal awards of a person that are not decided yet.
 *
 * @param drafts  drafts
 * @param pending awards in review
 */
public record OpenAwards(long drafts, long pending) {
}
