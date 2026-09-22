package ua.edu.chnu.awards.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;
import ua.edu.chnu.awards.user.repository.UserRepository;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;

class TokenClaimsCustomizerTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
    private final TokenClaimsCustomizer customizer = new TokenClaimsCustomizer(userRepository, userRoleRepository,
        new RolePermissions(), Clock.systemUTC());

    @Test
    void ac14_accessTokenCarriesIdentityRolesPermissionsAndOrganisation() {
        Organization faculty = Organization.builder().id(9L).orgType(OrganizationType.FACULTY).build();
        User user = User.builder().id(3L).emailAddress("dean.fmi@chnu.edu.ua").firstName("Martyn")
            .lastName("Martyniuk").organization(faculty).build();
        when(userRepository.findByEmailAddressIgnoreCase("dean.fmi@chnu.edu.ua")).thenReturn(Optional.of(user));
        Organization department = Organization.builder().id(64L).orgType(OrganizationType.DEPARTMENT).build();
        when(userRoleRepository.findCurrentByUserId(eq(3L), any(LocalDate.class))).thenReturn(List.of(
            UserRole.builder().roleType(RoleType.DEAN).organization(faculty).build(),
            UserRole.builder().roleType(RoleType.EMPLOYEE).organization(department).build(),
            UserRole.builder().roleType(RoleType.DEAN).organization(faculty).build()));
        JwtEncodingContext context = context(OAuth2TokenType.ACCESS_TOKEN, "dean.fmi@chnu.edu.ua");

        customizer.customize(context);

        Map<String, Object> claims = context.getClaims().build().getClaims();
        assertThat(claims).containsEntry("sub", "3")
            .containsEntry("email", "dean.fmi@chnu.edu.ua")
            .containsEntry("name", "Martyn Martyniuk")
            .containsEntry("roles", List.of("EMPLOYEE", "DEAN"))
            .containsEntry("role_scopes", List.of("DEAN:9", "EMPLOYEE:64"))
            .containsEntry("org_id", "9")
            .containsEntry("org_type", "FACULTY");
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) claims.get("permissions");
        assertThat(permissions).contains("award:approve:level2", "award:read:faculty", "user:read:scope",
            "user:manage:scope").doesNotContain("user:manage", "user:read:all");
        assertThat(claims).containsEntry("token_use", "access");
    }

    @Test
    void ac14_idTokenHasNoPermissionsClaim() {
        User user = User.builder().id(3L).emailAddress("e@chnu.edu.ua").firstName("A").lastName("B")
            .organization(Organization.builder().id(64L).orgType(OrganizationType.DEPARTMENT).build()).build();
        when(userRepository.findByEmailAddressIgnoreCase("e@chnu.edu.ua")).thenReturn(Optional.of(user));
        when(userRoleRepository.findCurrentByUserId(eq(3L), any(LocalDate.class))).thenReturn(List.of());
        JwtEncodingContext context = context(new OAuth2TokenType("id_token"), "e@chnu.edu.ua");

        customizer.customize(context);

        Map<String, Object> claims = context.getClaims().build().getClaims();
        assertThat(claims).containsEntry("roles", List.of()).containsEntry("role_scopes", List.of())
            .doesNotContainKey("permissions")
            .doesNotContainKey("token_use");
    }

    @Test
    void refreshTokensAreLeftAloneAndUnknownUsersGetNoToken() {
        customizer.customize(context(OAuth2TokenType.REFRESH_TOKEN, "e@chnu.edu.ua"));
        verifyNoInteractions(userRepository);

        when(userRepository.findByEmailAddressIgnoreCase("ghost@chnu.edu.ua")).thenReturn(Optional.empty());
        JwtEncodingContext context = context(OAuth2TokenType.ACCESS_TOKEN, "ghost@chnu.edu.ua");
        assertThatThrownBy(() -> customizer.customize(context)).isInstanceOf(OAuth2AuthenticationException.class);
    }

    private static JwtEncodingContext context(OAuth2TokenType type, String principalName) {
        RegisteredClient client = RegisteredClient.withId("c").clientId("c")
            .redirectUri("http://localhost/cb")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .build();
        return JwtEncodingContext.with(JwsHeader.with(SignatureAlgorithm.RS256), JwtClaimsSet.builder().subject("x"))
            .registeredClient(client)
            .principal(new TestingAuthenticationToken(principalName, "n/a"))
            .tokenType(type)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .build();
    }
}
