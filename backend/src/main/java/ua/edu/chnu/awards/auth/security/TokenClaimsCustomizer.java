package ua.edu.chnu.awards.auth.security;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.authz.DelegatedScope;
import ua.edu.chnu.awards.authz.RoleScope;
import ua.edu.chnu.awards.delegation.entity.RoleDelegation;
import ua.edu.chnu.awards.delegation.repository.RoleDelegationRepository;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;
import ua.edu.chnu.awards.user.repository.UserRepository;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Adds identity, role, permission and organisation claims to access and id tokens. Claim collections are plain
 * lists because the stored token metadata is serialised with type information.
 */
@Component
@RequiredArgsConstructor
public class TokenClaimsCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_NAME = "name";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_ROLE_SCOPES = "role_scopes";
    public static final String CLAIM_HELD_SCOPES = "held_scopes";
    public static final String CLAIM_DELEGATIONS = "delegations";
    public static final String CLAIM_PERMISSIONS = "permissions";
    public static final String CLAIM_ORG_ID = "org_id";
    public static final String CLAIM_ORG_TYPE = "org_type";
    public static final String CLAIM_TOKEN_USE = "token_use";
    public static final String TOKEN_USE_ACCESS = "access";

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleDelegationRepository delegationRepository;
    private final RolePermissions rolePermissions;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public void customize(JwtEncodingContext context) {
        boolean accessToken = OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType());
        boolean idToken = OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue());
        if (!accessToken && !idToken) {
            return;
        }
        String email = context.getPrincipal().getName();
        User user = userRepository.findByEmailAddressIgnoreCase(email)
            .orElseThrow(() -> new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT));
        LocalDate today = LocalDate.now(clock);
        List<UserRole> assignments = userRoleRepository.findCurrentByUserId(user.getId(), today);
        List<RoleDelegation> borrowed = delegationRepository.findCurrentByDelegateId(user.getId(), today);
        List<RoleType> roles = assignments.stream()
            .map(UserRole::getRoleType)
            .distinct()
            .sorted()
            .toList();
        List<String> held = assignments.stream()
            .map(assignment -> new RoleScope(assignment.getRoleType(), assignment.getOrganization().getId()).toClaim())
            .distinct()
            .sorted()
            .toList();
        Set<String> scopes = new TreeSet<>(held);
        List<String> delegations = borrowed.stream()
            .map(TokenClaimsCustomizer::toClaim)
            .distinct()
            .sorted()
            .toList();
        borrowed.forEach(delegation -> scopes.add(new RoleScope(delegation.getRoleType(),
            delegation.getOrganization().getId()).toClaim()));
        context.getClaims().claims(claims -> {
            claims.put("sub", String.valueOf(user.getId()));
            claims.put(CLAIM_EMAIL, user.getEmailAddress());
            claims.put(CLAIM_NAME, user.getFullName());
            claims.put(CLAIM_ROLES, new ArrayList<>(roles.stream().map(Enum::name).toList()));
            claims.put(CLAIM_ROLE_SCOPES, new ArrayList<>(scopes));
            claims.put(CLAIM_HELD_SCOPES, new ArrayList<>(held));
            claims.put(CLAIM_DELEGATIONS, new ArrayList<>(delegations));
            claims.put(CLAIM_ORG_ID, String.valueOf(user.getOrganization().getId()));
            claims.put(CLAIM_ORG_TYPE, user.getOrganization().getOrgType().name());
            if (accessToken) {
                claims.put(CLAIM_PERMISSIONS, new ArrayList<>(permissions(roles, borrowed)));
                claims.put(CLAIM_TOKEN_USE, TOKEN_USE_ACCESS);
            }
        });
    }

    private List<String> permissions(List<RoleType> roles, List<RoleDelegation> borrowed) {
        Set<String> all = new LinkedHashSet<>(rolePermissions.union(roles));
        borrowed.forEach(delegation -> all.addAll(rolePermissions.delegableOf(delegation.getRoleType())));
        return List.copyOf(new TreeSet<>(all));
    }

    private static String toClaim(RoleDelegation delegation) {
        return new DelegatedScope(delegation.getRoleType(), delegation.getOrganization().getId(),
            delegation.getDelegator().getId()).toClaim();
    }
}
