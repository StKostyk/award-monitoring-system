package ua.edu.chnu.awards.analytics.dto;

import ua.edu.chnu.awards.organization.entity.OrganizationType;

/**
 * A faculty or department the caller may analyse.
 *
 * @param id       identifier
 * @param name     English name
 * @param nameUk   Ukrainian name
 * @param code     short code
 * @param type     {@code FACULTY} or {@code DEPARTMENT}
 * @param parentId the parent organisation, null for the root
 */
public record AnalysableUnit(Long id, String name, String nameUk, String code, OrganizationType type,
                             Long parentId) {
}
