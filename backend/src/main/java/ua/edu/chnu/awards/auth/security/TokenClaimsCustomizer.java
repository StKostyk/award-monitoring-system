package ua.edu.chnu.awards.auth.security;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    public static final String CLAIM_PERMISSIONS = "permissions";
    public static final String CLAIM_ORG_ID = "org_id";
    public static final String CLAIM_ORG_TYPE = "org_type";

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissions rolePermissions;

    @Override
    @Transactional(readOnly = true)
    public void customize(JwtEncodingContext context) {
        boolean accessToken = OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType());
        boolean idToken = OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue());
        if (!accessToken && !idToken) {
            return;
        }
        String email = context.getPrincipal().getName();
        User user = userRepository.findByEmailAddressIgnoreCase(email).orElse(null);
        if (user == null) {
            return;
        }
        List<RoleType> roles = userRoleRepository.findCurrentByUserId(user.getId(), LocalDate.now()).stream()
            .map(UserRole::getRoleType)
            .distinct()
            .sorted()
            .toList();
        context.getClaims().claims(claims -> {
            claims.put("sub", String.valueOf(user.getId()));
            claims.put(CLAIM_EMAIL, user.getEmailAddress());
            claims.put(CLAIM_NAME, user.getFullName());
            claims.put(CLAIM_ROLES, new ArrayList<>(roles.stream().map(Enum::name).toList()));
            claims.put(CLAIM_ORG_ID, String.valueOf(user.getOrganization().getId()));
            claims.put(CLAIM_ORG_TYPE, user.getOrganization().getOrgType().name());
            if (accessToken) {
                claims.put(CLAIM_PERMISSIONS, new ArrayList<>(rolePermissions.union(roles)));
            }
        });
    }
}
