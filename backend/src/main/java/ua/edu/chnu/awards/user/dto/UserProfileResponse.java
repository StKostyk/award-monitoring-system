package ua.edu.chnu.awards.user.dto;

import java.time.Instant;
import java.util.List;

import ua.edu.chnu.awards.user.entity.AccountStatus;

/**
 * Profile of the signed-in user.
 *
 * @param id           identifier
 * @param email        email address
 * @param firstName    first name
 * @param lastName     last name
 * @param roles        roles in effect today
 * @param organization primary organisation
 * @param status       account status
 * @param createdAt    registration time
 * @param lastLoginAt  last successful sign-in, null before the first one
 * @param membershipConfirmed whether somebody has ever granted the user a role; false until the department
 *                            confirms a self-registered account
 */
public record UserProfileResponse(Long id, String email, String firstName, String lastName,
                                  List<RoleAssignmentResponse> roles, OrganizationRef organization,
                                  AccountStatus status, Instant createdAt, Instant lastLoginAt,
                                  boolean membershipConfirmed) {
}
