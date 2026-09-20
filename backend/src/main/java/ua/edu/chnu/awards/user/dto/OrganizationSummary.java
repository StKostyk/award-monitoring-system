package ua.edu.chnu.awards.user.dto;

import ua.edu.chnu.awards.user.entity.OrganizationType;

/**
 * An organisation with its parent, for pickers.
 *
 * @param id     identifier
 * @param name   English name
 * @param nameUk Ukrainian name
 * @param code   short code
 * @param type   hierarchy level
 * @param parent the unit it belongs to, null for the root
 */
public record OrganizationSummary(Long id, String name, String nameUk, String code, OrganizationType type,
                                  OrganizationRef parent) {
}
