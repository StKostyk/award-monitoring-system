package ua.edu.chnu.awards.user.dto;

import ua.edu.chnu.awards.user.entity.OrganizationType;

/**
 * Organisation reference as exposed by the API.
 *
 * @param id     identifier
 * @param name   English name
 * @param nameUk Ukrainian name
 * @param code   short code
 * @param type   hierarchy level
 */
public record OrganizationRef(Long id, String name, String nameUk, String code, OrganizationType type) {
}
