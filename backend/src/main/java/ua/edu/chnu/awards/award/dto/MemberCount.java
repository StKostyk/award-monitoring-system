package ua.edu.chnu.awards.award.dto;

/**
 * Active accounts whose organisation is one organisation.
 *
 * @param organizationId the organisation
 * @param members        number of active accounts
 */
public record MemberCount(long organizationId, long members) {
}
