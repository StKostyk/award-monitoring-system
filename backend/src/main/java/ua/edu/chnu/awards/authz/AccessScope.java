package ua.edu.chnu.awards.authz;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import ua.edu.chnu.awards.auth.security.RolePermissions;
import ua.edu.chnu.awards.auth.security.TokenClaimsCustomizer;
import ua.edu.chnu.awards.user.entity.RoleType;

import lombok.RequiredArgsConstructor;

/**
 * Authorisation questions about the caller, answered from the access token and the organisation tree; used in
 * {@code @PreAuthorize} expressions as {@code @access}. A refusal records what was missing on the current
 * request so the 403 body and the audit row can say it.
 */
@Component("access")
@RequiredArgsConstructor
public class AccessScope {

    public static final String MISSING_ATTRIBUTE = AccessScope.class.getName() + ".missing";
    static final String PERMISSION_READ_ALL = "user:read:all";
    static final String PERMISSION_READ_SCOPE = "user:read:scope";

    private final OrganizationTree tree;
    private final RoleLevels levels;
    private final RolePermissions permissions;

    /**
     * Whether the caller holds a permission.
     *
     * @param permission permission string
     * @return true when the token carries it
     */
    public boolean has(String permission) {
        return current().map(auth -> auth.getAuthorities().stream()
            .anyMatch(granted -> granted.getAuthority().equals(permission))).orElse(false);
    }

    /**
     * Whether the caller holds at least one of the permissions; records the shortfall otherwise.
     *
     * @param anyOf acceptable permissions
     * @return true when one is held
     */
    public boolean require(String... anyOf) {
        for (String permission : anyOf) {
            if (has(permission)) {
                return grant();
            }
        }
        return refuse("permission " + String.join(" or ", anyOf) + " is required");
    }

    /**
     * Whether one of the caller's role scopes covers the organisation.
     *
     * @param organizationId the organisation
     * @return true when the organisation is inside a scope
     */
    public boolean inScope(long organizationId) {
        return scopes().stream().anyMatch(scope -> tree.covers(scope.organizationId(), organizationId)) && grant()
            || refuse("organisation " + organizationId + " is outside your scope");
    }

    /**
     * Whether the caller may read the members of the organisation: {@code user:read:all}, or a role that grants
     * {@code user:read:scope} whose scope covers it. A role without directory rights (such as {@code EMPLOYEE}
     * in another department) does not widen what the caller can see.
     *
     * @param organizationId the organisation
     * @return true when readable
     */
    public boolean canRead(long organizationId) {
        return has(PERMISSION_READ_ALL) && grant()
            || readScopes().stream().anyMatch(scope -> tree.covers(scope.organizationId(), organizationId)) && grant()
            || refuse("organisation " + organizationId + " is outside your scope");
    }

    /**
     * Whether the caller's highest role anywhere outranks the role.
     *
     * @param role the role to grant
     * @return true when the caller may grant it somewhere
     */
    public boolean below(RoleType role) {
        return scopes().stream().anyMatch(scope -> levels.above(scope.role(), role)) && grant()
            || refuse("no role above " + role);
    }

    /**
     * Whether the caller may grant the role in the organisation: a scope covering the organisation whose role
     * outranks it.
     *
     * @param role           the role to grant
     * @param organizationId where it would apply
     * @return true when allowed
     */
    public boolean canManage(RoleType role, long organizationId) {
        return scopes().stream().anyMatch(scope -> tree.covers(scope.organizationId(), organizationId)
            && levels.above(scope.role(), role)) && grant()
            || refuse("no role above " + role + " in the scope of organisation " + organizationId);
    }

    /**
     * The caller's role scopes from the token.
     *
     * @return scopes, empty for an anonymous caller
     */
    public List<RoleScope> scopes() {
        return current().filter(JwtAuthenticationToken.class::isInstance)
            .map(JwtAuthenticationToken.class::cast)
            .map(token -> token.getToken().getClaimAsStringList(TokenClaimsCustomizer.CLAIM_ROLE_SCOPES))
            .map(claims -> claims.stream().map(RoleScope::parse).flatMap(Optional::stream).toList())
            .orElse(List.of());
    }

    /**
     * Organisations whose members the caller may read.
     *
     * @return empty when everything is readable ({@code user:read:all}); otherwise the ids inside the scopes of
     *         the roles that grant {@code user:read:scope}
     */
    public Optional<Set<Long>> readableOrganizations() {
        if (has(PERMISSION_READ_ALL)) {
            return Optional.empty();
        }
        Set<Long> ids = new HashSet<>();
        readScopes().forEach(scope -> ids.addAll(tree.subtree(scope.organizationId())));
        return Optional.of(ids);
    }

    private List<RoleScope> readScopes() {
        return scopes().stream().filter(scope -> permissions.of(scope.role()).contains(PERMISSION_READ_SCOPE)).toList();
    }

    /**
     * An organisation and its descendants, for narrowing a directory query.
     *
     * @param organizationId the root
     * @return ids of the subtree, empty for an unknown organisation
     */
    public Set<Long> subtreeOf(long organizationId) {
        return tree.subtree(organizationId);
    }

    /**
     * The caller's user id.
     *
     * @return the token subject
     * @throws IllegalStateException without a bearer token
     */
    public long callerId() {
        return current().filter(JwtAuthenticationToken.class::isInstance)
            .map(auth -> Long.parseLong(((JwtAuthenticationToken) auth).getToken().getSubject()))
            .orElseThrow(() -> new IllegalStateException("No signed-in caller"));
    }

    private static Optional<Authentication> current() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
            .filter(Authentication::isAuthenticated)
            .filter(JwtAuthenticationToken.class::isInstance);
    }

    private static boolean refuse(String missing) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            attributes.setAttribute(MISSING_ATTRIBUTE, missing, RequestAttributes.SCOPE_REQUEST);
        }
        return false;
    }

    private static boolean grant() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            attributes.removeAttribute(MISSING_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        }
        return true;
    }
}
