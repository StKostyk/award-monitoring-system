package ua.edu.chnu.awards.user.dto;

import java.time.Instant;
import java.util.List;

import ua.edu.chnu.awards.organization.dto.OrganizationRef;
import ua.edu.chnu.awards.user.entity.AccountStatus;

/**
 * A directory row.
 *
 * @param id                  identifier
 * @param email               email address
 * @param firstName           first name
 * @param lastName            last name
 * @param organization        primary organisation
 * @param status              account status
 * @param roles               roles in effect today
 * @param membershipConfirmed whether somebody has ever granted the user a role
 * @param lastLoginAt         last successful sign-in, null before the first one and for erased accounts
 */
public record UserSummaryResponse(Long id, String email, String firstName, String lastName,
                                  OrganizationRef organization, AccountStatus status,
                                  List<RoleAssignmentResponse> roles, boolean membershipConfirmed,
                                  Instant lastLoginAt) {

    /**
     * Copies the role list.
     */
    public UserSummaryResponse {
        roles = List.copyOf(roles);
    }
}
