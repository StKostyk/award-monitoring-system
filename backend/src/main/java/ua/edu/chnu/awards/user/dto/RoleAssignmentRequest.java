package ua.edu.chnu.awards.user.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

import ua.edu.chnu.awards.user.entity.RoleType;

/**
 * Request to grant a role to a user.
 *
 * @param role               the role to grant
 * @param organizationId     where the role applies
 * @param validFrom          first day the role applies; today when null
 * @param validTo            last day the role applies; open-ended when null
 * @param updateOrganization membership confirmation: move the user's primary organisation to
 *                           {@code organizationId} and end a current {@code EMPLOYEE} role elsewhere
 * @param fromOrganizationId the user's current organisation as the caller saw it; required to move a user
 *                           who has already held a role, so that a stale confirmation cannot move them back
 */
public record RoleAssignmentRequest(@NotNull RoleType role, @NotNull Long organizationId, LocalDate validFrom,
                                    LocalDate validTo, boolean updateOrganization, Long fromOrganizationId) {
}
