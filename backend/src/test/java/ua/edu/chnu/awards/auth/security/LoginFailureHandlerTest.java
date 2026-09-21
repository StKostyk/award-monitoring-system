package ua.edu.chnu.awards.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.auth.service.LoginAttemptService;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

class LoginFailureHandlerTest {

    private static final String DEAN = "dean@chnu.edu.ua";

    private final LoginAttemptService attempts = mock(LoginAttemptService.class);
    private final AuditService audit = mock(AuditService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final LoginFailureHandler handler = new LoginFailureHandler(attempts, audit, userRepository);

    @Test
    void ac16_statusRefusalRedirectsWithTheStatusCode() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request("Dean@chnu.edu.ua"), response,
            new AccountStatusRefusedException(AccountStatus.SUSPENDED));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=SUSPENDED");
        verify(attempts, never()).recordFailure(any(), any());
    }

    @Test
    void ac41_ac43_wrongPasswordIsCountedAndAuditedWithTheAccount() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(userRepository.findByEmailAddressIgnoreCase(DEAN))
            .thenReturn(Optional.of(User.builder().id(5L).emailAddress(DEAN).build()));

        handler.onAuthenticationFailure(request("Dean@chnu.edu.ua"), response, new BadCredentialsException("bad"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=BAD_CREDENTIALS");
        verify(attempts).recordFailure(DEAN, "203.0.113.7");
        verify(audit).record(AuditAction.LOGIN_FAILED, 5L, Map.of("email", DEAN, "reason", "BAD_CREDENTIALS"));
    }

    @Test
    void ac41_theFailureThatLocksTheAccountAlreadyShowsLocked() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(attempts.recordFailure(DEAN, "203.0.113.7")).thenReturn(true);

        handler.onAuthenticationFailure(request(DEAN), response, new BadCredentialsException("bad"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=LOCKED");
    }

    @Test
    void ac41_unknownAddressIsAuditedWithoutTheTypedValueAndStaysLockedLikeAnAccount() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(attempts.isLocked("ghost@chnu.edu.ua")).thenReturn(true);

        handler.onAuthenticationFailure(request("ghost@chnu.edu.ua"), response, new BadCredentialsException("bad"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=LOCKED");
        verify(attempts, never()).recordFailure(any(), any());
        verify(audit).record(AuditAction.LOGIN_FAILED, null, Map.of("reason", "LOCKED"));
    }

    @Test
    void ac41_lockedExceptionRedirectsWithLockedAndIsNotCountedAgain() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request(null), response, new LockedException("locked"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=LOCKED");
        verify(attempts, never()).recordFailure(any(), any());
        verify(audit).record(eq(AuditAction.LOGIN_FAILED), eq(null), any());
    }

    private static MockHttpServletRequest request(String username) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (username != null) {
            request.setParameter("username", username);
        }
        request.setRemoteAddr("203.0.113.7");
        return request;
    }
}
