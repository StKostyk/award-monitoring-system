package ua.edu.chnu.awards.award.dto;

import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;

/**
 * A faculty or department as a recipient of awards.
 *
 * @param id     identifier
 * @param name   English name
 * @param nameUk Ukrainian name
 * @param type   faculty or department
 */
public record UnitRef(Long id, String name, String nameUk, OrganizationType type) {

    /**
     * The reference of an organisation.
     *
     * @param organization the organisation
     * @return reference
     */
    public static UnitRef of(Organization organization) {
        return new UnitRef(organization.getId(), organization.getName(), organization.getNameUk(),
            organization.getOrgType());
    }
}
