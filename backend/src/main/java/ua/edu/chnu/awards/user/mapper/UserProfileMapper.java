package ua.edu.chnu.awards.user.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.user.dto.OrganizationRef;
import ua.edu.chnu.awards.user.dto.RoleAssignmentResponse;
import ua.edu.chnu.awards.user.dto.UserProfileResponse;
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

    RoleAssignmentResponse toAssignment(UserRole role) {
        return new RoleAssignmentResponse(role.getRoleType(), toRef(role.getOrganization()),
            role.getValidFrom(), role.getValidTo());
    }

    OrganizationRef toRef(Organization organization) {
        return new OrganizationRef(organization.getId(), organization.getName(), organization.getNameUk(),
            organization.getCode(), organization.getOrgType());
    }
}
