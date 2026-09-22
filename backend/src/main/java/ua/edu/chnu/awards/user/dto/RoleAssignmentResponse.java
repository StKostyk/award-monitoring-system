package ua.edu.chnu.awards.user.dto;

import java.time.LocalDate;

import ua.edu.chnu.awards.user.entity.RoleType;

/**
 * A role held within an organisation.
 *
 * @param id           assignment identifier
 * @param role         the role
 * @param organization scope of the role
 * @param validFrom    first day the role applies
 * @param validTo      last day the role applies, null when open-ended
 */
public record RoleAssignmentResponse(Long id, RoleType role, OrganizationRef organization, LocalDate validFrom,
                                     LocalDate validTo) {
}
