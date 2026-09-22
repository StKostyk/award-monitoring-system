package ua.edu.chnu.awards.user.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.user.dto.OrganizationRef;
import ua.edu.chnu.awards.user.dto.RoleAssignmentResponse;
import ua.edu.chnu.awards.user.dto.UserDetailResponse;
import ua.edu.chnu.awards.user.dto.UserProfileResponse;
import ua.edu.chnu.awards.user.dto.UserSummaryResponse;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;

/**
 * Converts user entities to API responses.
 */
@Component
public class UserProfileMapper {

    /**
     * Builds the profile response.
     *
     * @param user  the user
     * @param roles the user's current role assignments
     * @return response
     */
    public UserProfileResponse toProfile(User user, List<UserRole> roles) {
        return new UserProfileResponse(
            user.getId(),
            user.getEmailAddress(),
            user.getFirstName(),
            user.getLastName(),
            roles.stream().map(this::toAssignment).toList(),
            toRef(user.getOrganization()),
            user.getAccountStatus(),
            user.getCreatedAt(),
            user.getLastLoginAt());
    }

    /**
     * Builds a directory row.
     *
     * @param user      the user
     * @param roles     the user's current role assignments
     * @param confirmed whether the user has ever held a role
     * @return response
     */
    public UserSummaryResponse toSummary(User user, List<UserRole> roles, boolean confirmed) {
        return new UserSummaryResponse(user.getId(), user.getEmailAddress(), user.getFirstName(),
            user.getLastName(), toRef(user.getOrganization()), user.getAccountStatus(),
            roles.stream().map(this::toAssignment).toList(), confirmed);
    }

    /**
     * Builds the detail view with the role history.
     *
     * @param user    the user
     * @param current the user's current role assignments
     * @param history every assignment, newest first
     * @return response
     */
    public UserDetailResponse toDetail(User user, List<UserRole> current, List<UserRole> history) {
        return new UserDetailResponse(user.getId(), user.getEmailAddress(), user.getFirstName(),
            user.getLastName(), toRef(user.getOrganization()), user.getAccountStatus(), user.getCreatedAt(),
            user.getLastLoginAt(), current.stream().map(this::toAssignment).toList(),
            history.stream().map(this::toAssignment).toList(), !history.isEmpty());
    }

    RoleAssignmentResponse toAssignment(UserRole role) {
        return new RoleAssignmentResponse(role.getId(), role.getRoleType(), toRef(role.getOrganization()),
            role.getValidFrom(), role.getValidTo());
    }

    OrganizationRef toRef(Organization organization) {
        return new OrganizationRef(organization.getId(), organization.getName(), organization.getNameUk(),
            organization.getCode(), organization.getOrgType());
    }
}
