package ua.edu.chnu.awards.authz;

import java.util.Optional;

import ua.edu.chnu.awards.user.entity.RoleType;

/**
 * A role held within an organisation, as carried by the {@code role_scopes} token claim ({@code ROLE:orgId}).
 *
 * @param role           the role
 * @param organizationId the organisation the role is scoped to
 */
public record RoleScope(RoleType role, long organizationId) {

    /**
     * Claim form of the scope.
     *
     * @return {@code ROLE:orgId}
     */
    public String toClaim() {
        return role.name() + ":" + organizationId;
    }

    /**
     * Parses a claim entry, ignoring anything malformed.
     *
     * @param claim {@code ROLE:orgId}
     * @return the scope, or empty when the entry cannot be read
     */
    public static Optional<RoleScope> parse(String claim) {
        int colon = claim == null ? -1 : claim.indexOf(':');
        if (colon <= 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(new RoleScope(RoleType.valueOf(claim.substring(0, colon)),
                Long.parseLong(claim.substring(colon + 1))));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
