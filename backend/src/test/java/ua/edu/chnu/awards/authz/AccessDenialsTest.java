package ua.edu.chnu.awards.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorityAuthorizationDecision;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditLog;
import ua.edu.chnu.awards.audit.service.AuditService;

class AccessDenialsTest {

    private final AuditService audit = mock(AuditService.class);
    private final AccessDenials denials = new AccessDenials(audit);
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");

    @BeforeEach
    void setUp() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void ac15_aDenialOfASignedInCallerIsAuditedWithTheRequestAndTheMissingRequirement() {
        request.setAttribute(AccessScope.MISSING_ATTRIBUTE, "permission user:read:scope is required");
        JwtAuthenticationToken caller = caller(7L);

        denials.onDenied(new AuthorizationDeniedEvent<>(() -> caller, "users", new AuthorizationDecision(false)));

        verify(audit).record(AuditAction.ACCESS_DENIED, AuditLog.AUTHORIZATION, 7L,
            Map.of("method", "GET", "path", "/api/v1/users", "required", "permission user:read:scope is required"));
    }

    @Test
    void ac15_anonymousDenialsAreNotAudited() {
        AnonymousAuthenticationToken anonymous = new AnonymousAuthenticationToken("key", "anonymous",
            AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        denials.onDenied(new AuthorizationDeniedEvent<>(() -> anonymous, "users", new AuthorizationDecision(false)));

        verify(audit, never()).record(any(), anyString(), any(), any());
    }

    @Test
    void ac15_aRequestRuleDenialNamesTheRequiredAuthority() {
        JwtAuthenticationToken caller = caller(7L);

        denials.onDenied(new AuthorizationDeniedEvent<>(() -> caller, "actuator",
            new AuthorityAuthorizationDecision(false, AuthorityUtils.createAuthorityList("ROLE_SYSTEM_ADMIN"))));

        verify(audit).record(AuditAction.ACCESS_DENIED, AuditLog.AUTHORIZATION, 7L,
            Map.of("method", "GET", "path", "/api/v1/users", "required", "authority ROLE_SYSTEM_ADMIN is required"));
        assertThat(denials.problem(request).getDetail()).isEqualTo("authority ROLE_SYSTEM_ADMIN is required");
    }

    @Test
    void ac15_anAuditFailureDoesNotChangeTheRefusal() {
        doThrow(new QueryTimeoutException("down")).when(audit).record(any(), anyString(), any(), any());

        denials.onDenied(new AuthorizationDeniedEvent<>(() -> caller(7L), "users", new AuthorizationDecision(false)));

        assertThat(denials.problem(request).getStatus()).isEqualTo(403);
    }

    @Test
    void ac15_aNonNumericSubjectIsAuditedWithoutAUserId() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("service").issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60)).build();
        JwtAuthenticationToken caller = new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList("x"));

        denials.onDenied(new AuthorizationDeniedEvent<>(() -> caller, "users", new AuthorizationDecision(false)));

        verify(audit).record(eq(AuditAction.ACCESS_DENIED), eq(AuditLog.AUTHORIZATION), isNull(), any());
    }

    @Test
    void ac15_theProblemNamesWhatWasMissing() {
        request.setAttribute(AccessScope.MISSING_ATTRIBUTE, "organisation 10 is outside your scope");

        var problem = denials.problem(request);

        assertThat(problem.getStatus()).isEqualTo(403);
        assertThat(problem.getType()).hasToString("urn:awards:problem:access-denied");
        assertThat(problem.getDetail()).isEqualTo("organisation 10 is outside your scope");
        assertThat(denials.problem(new MockHttpServletRequest()).getDetail())
            .isEqualTo("You are not allowed to do this");
        assertThat(List.of(problem.getTitle())).containsExactly("Forbidden");
        assertThat(problem.getInstance()).hasToString("/api/v1/users");
    }

    private static JwtAuthenticationToken caller(long userId) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject(String.valueOf(userId))
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        return new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList("x"));
    }
}
