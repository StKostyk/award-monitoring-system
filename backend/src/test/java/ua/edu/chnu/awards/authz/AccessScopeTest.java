package ua.edu.chnu.awards.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import ua.edu.chnu.awards.auth.security.RolePermissions;
import ua.edu.chnu.awards.user.entity.RoleType;

class AccessScopeTest {

    private final OrganizationTree tree = mock(OrganizationTree.class);
    private final AccessScope access = new AccessScope(tree, new RoleLevels(), new RolePermissions());
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @BeforeEach
    void setUp() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(tree.covers(9L, 64L)).thenReturn(true);
        when(tree.covers(9L, 9L)).thenReturn(true);
        when(tree.covers(1L, 64L)).thenReturn(true);
        when(tree.covers(1L, 10L)).thenReturn(true);
        when(tree.subtree(9L)).thenReturn(Set.of(9L, 64L));
        when(tree.subtree(64L)).thenReturn(Set.of(64L));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void ac13_scopeCoversTheSubtreeOfEveryRoleScope() {
        signIn(7L, List.of("DEAN:9"), "user:read:scope");

        assertThat(access.inScope(64L)).isTrue();
        assertThat(access.inScope(9L)).isTrue();
        assertThat(access.inScope(10L)).isFalse();
        assertThat(request.getAttribute(AccessScope.MISSING_ATTRIBUTE))
            .isEqualTo("organisation 10 is outside your scope");
    }

    @Test
    void ac14_belowComparesWithTheHighestRoleHeld() {
        signIn(7L, List.of("EMPLOYEE:64", "DEAN:9"), "user:manage:scope");

        assertThat(access.below(RoleType.FACULTY_SECRETARY)).isTrue();
        assertThat(access.below(RoleType.DEAN)).isFalse();
        assertThat(access.below(RoleType.RECTOR)).isFalse();
    }

    @Test
    void ac14_canManageNeedsAScopeThatCoversTheOrganisationAndOutranksTheRole() {
        signIn(7L, List.of("EMPLOYEE:64", "DEAN:9"), "user:manage:scope");

        assertThat(access.canManage(RoleType.FACULTY_SECRETARY, 64L)).isTrue();
        assertThat(access.canManage(RoleType.FACULTY_SECRETARY, 10L)).isFalse();
        assertThat(access.canManage(RoleType.DEAN, 64L)).isFalse();
        assertThat(access.canManage(RoleType.RECTOR, 9L)).isFalse();
        assertThat(request.getAttribute(AccessScope.MISSING_ATTRIBUTE))
            .isEqualTo("no role above RECTOR in the scope of organisation 9");
    }

    @Test
    void ac13_administratorScopeIsTheWholeTree() {
        signIn(1L, List.of("SYSTEM_ADMIN:1"), "user:read:all", "user:manage");

        assertThat(access.inScope(64L)).isTrue();
        assertThat(access.inScope(10L)).isTrue();
        assertThat(access.canManage(RoleType.SYSTEM_ADMIN, 64L)).isTrue();
        assertThat(access.readableOrganizations()).isEmpty();
    }

    @Test
    void ac16_readableOrganisationsComeFromRolesThatGrantDirectoryReading() {
        when(tree.subtree(70L)).thenReturn(Set.of(70L));
        when(tree.covers(70L, 70L)).thenReturn(true);
        signIn(7L, List.of("EMPLOYEE:70", "DEAN:9"), "user:read:scope");

        assertThat(access.readableOrganizations()).contains(Set.of(9L, 64L));
        assertThat(access.canRead(64L)).isTrue();
        assertThat(access.canRead(70L)).isFalse();
        assertThat(access.inScope(70L)).isTrue();
    }

    @Test
    void ac15_aGrantClearsTheReasonOfAnEarlierRefusal() {
        signIn(7L, List.of("DEAN:9"), "user:read:scope");

        assertThat(access.inScope(10L)).isFalse();
        assertThat(access.require("user:read:scope")).isTrue();
        assertThat(request.getAttribute(AccessScope.MISSING_ATTRIBUTE)).isNull();
    }

    @Test
    void ac15_requireRecordsTheMissingPermission() {
        signIn(7L, List.of("EMPLOYEE:64"), "award:create");

        assertThat(access.require("user:read:all", "user:read:scope")).isFalse();
        assertThat(request.getAttribute(AccessScope.MISSING_ATTRIBUTE))
            .isEqualTo("permission user:read:all or user:read:scope is required");
        assertThat(access.has("award:create")).isTrue();
        assertThat(access.callerId()).isEqualTo(7L);
    }

    @Test
    void ac15_anonymousCallerHasNoScopeAndNoId() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymous",
            AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(access.inScope(64L)).isFalse();
        assertThat(access.require("award:create")).isFalse();
        assertThat(access.scopes()).isEmpty();
        assertThat(access.readableOrganizations()).contains(Set.of());
    }

    @Test
    void ac11_scopesIgnoreMalformedEntries() {
        signIn(7L, List.of("DEAN:9", "garbage", "NOPE:1", "DEAN:x"), "user:read:scope");

        assertThat(access.scopes()).containsExactly(new RoleScope(RoleType.DEAN, 9L));
    }

    private void signIn(long userId, List<String> scopes, String... permissions) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject(String.valueOf(userId))
            .claim("role_scopes", scopes).claim("permissions", List.of(permissions))
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList(permissions)));
    }
}
