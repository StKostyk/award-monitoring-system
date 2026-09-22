package ua.edu.chnu.awards.user.dto;

import java.time.Instant;
import java.util.List;

import ua.edu.chnu.awards.user.entity.AccountStatus;

/**
 * A user as seen by somebody who manages the directory.
 *
 * @param id                  identifier
 * @param email               email address
 * @param firstName           first name
 * @param lastName            last name
 * @param organization        primary organisation
 * @param status              account status
 * @param createdAt           registration time
 * @param lastLoginAt         last successful sign-in, null before the first one
 * @param roles               roles in effect today
 * @param roleHistory         every assignment ever made, newest first
 * @param membershipConfirmed whether somebody has ever granted the user a role
 */
public record UserDetailResponse(Long id, String email, String firstName, String lastName,
                                 OrganizationRef organization, AccountStatus status, Instant createdAt,
                                 Instant lastLoginAt, List<RoleAssignmentResponse> roles,
                                 List<RoleAssignmentResponse> roleHistory, boolean membershipConfirmed) {
}
