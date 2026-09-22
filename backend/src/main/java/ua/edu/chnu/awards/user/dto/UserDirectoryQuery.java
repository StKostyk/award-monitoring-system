package ua.edu.chnu.awards.user.dto;

import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.RoleType;

/**
 * Filters of the user directory; every part is optional and they combine with AND.
 *
 * @param organizationId limit to members of this organisation and its descendants
 * @param role           limit to holders of this role today
 * @param status         limit to this account status
 * @param unconfirmed    true lists only users who never held a role
 * @param q              case-insensitive fragment of the name or address, ignored under two characters
 */
public record UserDirectoryQuery(Long organizationId, RoleType role, AccountStatus status, boolean unconfirmed,
                                 String q) {
}
