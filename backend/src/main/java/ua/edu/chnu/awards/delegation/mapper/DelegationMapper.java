package ua.edu.chnu.awards.delegation.mapper;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.delegation.dto.DelegationResponse;
import ua.edu.chnu.awards.delegation.dto.UserBrief;
import ua.edu.chnu.awards.delegation.entity.RoleDelegation;
import ua.edu.chnu.awards.user.dto.OrganizationRef;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.User;

/**
 * Converts delegation entities to API responses.
 */
@Component
public class DelegationMapper {

    /**
     * Builds the response for one delegation.
     *
     * @param delegation the delegation
     * @param today      the day its state is judged on
     * @return response
     */
    public DelegationResponse toResponse(RoleDelegation delegation, LocalDate today) {
        return new DelegationResponse(
            delegation.getId(),
            delegation.getRoleType(),
            toRef(delegation.getOrganization()),
            toBrief(delegation.getDelegator()),
            toBrief(delegation.getDelegate()),
            delegation.getValidFrom(),
            delegation.getValidTo(),
            delegation.getReason(),
            delegation.stateOn(today),
            delegation.getCreatedAt(),
            delegation.getRevokedAt());
    }

    /**
     * Builds the short form of a person.
     *
     * @param user the person
     * @return response
     */
    public UserBrief toBrief(User user) {
        return new UserBrief(user.getId(), user.getFirstName(), user.getLastName(), user.getEmailAddress());
    }

    private static OrganizationRef toRef(Organization organization) {
        return new OrganizationRef(organization.getId(), organization.getName(), organization.getNameUk(),
            organization.getCode(), organization.getOrgType());
    }
}
