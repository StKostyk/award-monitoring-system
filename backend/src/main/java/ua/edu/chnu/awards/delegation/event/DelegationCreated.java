package ua.edu.chnu.awards.delegation.event;

import java.time.LocalDate;

import ua.edu.chnu.awards.user.entity.RoleType;

/**
 * Approval authority was handed over; the delegate is told once the surrounding transaction commits.
 *
 * @param email          recipient, the delegate
 * @param firstName      used in the greeting
 * @param role           the role lent
 * @param organization   English name of the organisation the role applies to
 * @param organizationUk Ukrainian name of the organisation, the English one when it has none
 * @param delegator      full name of the person who lent it
 * @param validFrom      first day the authority applies
 * @param validTo        last day the authority applies
 * @param reason         why it was handed over, null when none was given
 */
public record DelegationCreated(String email, String firstName, RoleType role, String organization,
                                String organizationUk, String delegator, LocalDate validFrom,
                                LocalDate validTo, String reason) {
}
