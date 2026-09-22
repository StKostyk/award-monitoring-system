package ua.edu.chnu.awards.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import ua.edu.chnu.awards.authz.AccessDenials;

class ApiExceptionHandlerTest {

    private final AccessDenials denials = mock(AccessDenials.class);
    private final ApiExceptionHandler handler = new ApiExceptionHandler(denials);
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
    private final AccessDeniedException refused = new AccessDeniedException("no");

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ac15_aSignedInCallerGetsTheTypedForbiddenBody() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("7").issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList("award:create")));
        ProblemDetail expected = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        when(denials.problem(request)).thenReturn(expected);

        assertThat(handler.accessDenied(request, refused)).isSameAs(expected);
    }

    @Test
    void ac15_anAnonymousDenialIsLeftToTheEntryPoint() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymous",
            AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThatThrownBy(() -> handler.accessDenied(request, refused)).isSameAs(refused);
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> handler.accessDenied(request, refused)).isSameAs(refused);
    }
}
