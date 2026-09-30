package ua.edu.chnu.awards.audit.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import ua.edu.chnu.awards.common.web.ClientRequest;

class AuditContextBinderTest {

    private final AuditContextBinder binder = new AuditContextBinder();
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement statement = mock(PreparedStatement.class);

    @BeforeEach
    void setUp() throws SQLException {
        when(connection.prepareStatement(AuditContextBinder.BIND)).thenReturn(statement);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void ac1_6_theSignedInCallerAndTheCorrelationIdAreBoundToTheTransaction() throws SQLException {
        UUID correlation = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ClientRequest.CORRELATION_ATTRIBUTE, correlation);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        signIn("21");

        binder.bind(connection);

        verify(statement).setString(1, "21");
        verify(statement).setString(2, correlation.toString());
        verify(statement).execute();
        verify(statement).close();
    }

    @Test
    void ac1_6_outsideARequestTheCorrelationIsLeftEmpty() throws SQLException {
        signIn("21");

        binder.bind(connection);

        verify(statement).setString(1, "21");
        verify(statement).setString(2, "");
    }

    @Test
    void ac1_6_withoutASignedInCallerNothingIsBound() throws SQLException {
        binder.bind(connection);

        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymous",
            List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        binder.bind(connection);

        verify(connection, never()).prepareStatement(anyString());
    }

    private static void signIn(String subject) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject(subject).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }
}
