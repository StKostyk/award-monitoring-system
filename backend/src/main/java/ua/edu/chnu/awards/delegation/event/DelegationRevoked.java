package ua.edu.chnu.awards.delegation.event;

import java.util.List;

import ua.edu.chnu.awards.user.entity.RoleType;

/**
 * Approval authority was taken back before its last day; the delegate is told, and the delegator too when
 * somebody else did it.
 *
 * @param recipients     addresses to tell
 * @param role           the role that was lent
 * @param organization   English name of the organisation the role applied to
 * @param organizationUk Ukrainian name of the organisation, the English one when it has none
 * @param delegate       full name of the person who borrowed the authority
 * @param actor          full name of the person who took it back
 */
public record DelegationRevoked(List<String> recipients, RoleType role, String organization,
                                String organizationUk, String delegate, String actor) {

    public DelegationRevoked {
        recipients = List.copyOf(recipients);
    }
}
