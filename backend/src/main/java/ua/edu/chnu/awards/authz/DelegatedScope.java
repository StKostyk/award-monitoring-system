package ua.edu.chnu.awards.authz;

import java.util.Optional;

import ua.edu.chnu.awards.user.entity.RoleType;

/**
 * Approval authority borrowed from somebody else, as carried by the {@code delegations} token claim
 * ({@code ROLE:orgId:delegatorId}). It grants the reading and approving permissions of the role and never the
 * authority to manage users or to lend the role on.
 *
 * @param role           the borrowed role
 * @param organizationId the organisation the borrowed role applies to
 * @param delegatorId    who lent it
 */
public record DelegatedScope(RoleType role, long organizationId, long delegatorId) {

    private static final int PARTS = 3;

    /**
     * Claim form of the borrowed authority.
     *
     * @return {@code ROLE:orgId:delegatorId}
     */
    public String toClaim() {
        return role.name() + ":" + organizationId + ":" + delegatorId;
    }

    /**
     * The role and organisation alone, as the {@code role_scopes} claim spells them.
     *
     * @return the scope the borrowed role covers
     */
    public RoleScope scope() {
        return new RoleScope(role, organizationId);
    }

    /**
     * Parses a claim entry, ignoring anything malformed.
     *
     * @param claim {@code ROLE:orgId:delegatorId}
     * @return the borrowed authority, or empty when the entry cannot be read
     */
    public static Optional<DelegatedScope> parse(String claim) {
        String[] parts = claim == null ? new String[0] : claim.split(":");
        if (parts.length != PARTS) {
            return Optional.empty();
        }
        try {
            return Optional.of(new DelegatedScope(RoleType.valueOf(parts[0]), Long.parseLong(parts[1]),
                Long.parseLong(parts[2])));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
