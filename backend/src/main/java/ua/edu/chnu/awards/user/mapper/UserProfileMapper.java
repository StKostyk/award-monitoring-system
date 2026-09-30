package ua.edu.chnu.awards.user.mapper;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.user.dto.OrganizationRef;
import ua.edu.chnu.awards.user.dto.RoleAssignmentResponse;
import ua.edu.chnu.awards.user.dto.UserDetailResponse;
import ua.edu.chnu.awards.user.dto.UserProfileResponse;
import ua.edu.chnu.awards.user.dto.UserSummaryResponse;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;

/**
 * Converts user entities to API responses.
 */
@Component
public class UserProfileMapper {

    private static final Set<OrganizationType> FACULTY_TYPES = EnumSet.of(OrganizationType.FACULTY,
        OrganizationType.COLLEGE);

    /**
     * Builds the profile response.
     *
     * @param user      the user
     * @param roles     the user's current role assignments
     * @param confirmed whether the user has ever held a role
     * @return response
     */
    public UserProfileResponse toProfile(User user, List<UserRole> roles, boolean confirmed) {
        return new UserProfileResponse(
            user.getId(),
            user.getEmailAddress(),
            user.getFirstName(),
            user.getLastName(),
            roles.stream().map(this::toAssignment).toList(),
            toRef(user.getOrganization()),
            facultyOf(user.getOrganization()),
            user.getAccountStatus(),
            user.getCreatedAt(),
            user.getLastLoginAt(),
            confirmed);
    }

    private OrganizationRef facultyOf(Organization organization) {
        Organization current = organization.getParent();
        while (current != null && !FACULTY_TYPES.contains(current.getOrgType())) {
            current = current.getParent();
        }
        return current == null ? null : toRef(current);
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

    /**
     * Builds one role assignment.
     *
     * @param role the assignment
     * @return response
     */
    public RoleAssignmentResponse toAssignment(UserRole role) {
        return new RoleAssignmentResponse(role.getId(), role.getRoleType(), toRef(role.getOrganization()),
            role.getValidFrom(), role.getValidTo());
    }

    OrganizationRef toRef(Organization organization) {
        return new OrganizationRef(organization.getId(), organization.getName(), organization.getNameUk(),
            organization.getCode(), organization.getOrgType());
    }
}
