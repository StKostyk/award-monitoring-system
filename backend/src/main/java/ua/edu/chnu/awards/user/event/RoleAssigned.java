package ua.edu.chnu.awards.user.event;

import java.time.LocalDate;

import ua.edu.chnu.awards.user.entity.RoleType;

/**
 * A role was granted; the holder is told once the surrounding transaction commits.
 *
 * @param email          recipient
 * @param firstName      used in the greeting
 * @param role           the role granted
 * @param organization   English name of the organisation the role applies to
 * @param organizationUk Ukrainian name of the organisation, the English one when it has none
 * @param actor          full name of the person who granted it
 * @param validFrom      first day the role applies
 * @param validTo        last day the role applies, null when open-ended
 */
public record RoleAssigned(String email, String firstName, RoleType role, String organization,
                           String organizationUk, String actor, LocalDate validFrom, LocalDate validTo) {
}
