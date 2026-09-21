package ua.edu.chnu.awards.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.auth.service.LoginAttemptService;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

class LoginSuccessListenerTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final LoginAttemptService attempts = mock(LoginAttemptService.class);
    private final AuditService audit = mock(AuditService.class);
    private final LoginSuccessListener listener = new LoginSuccessListener(userRepository, attempts, audit);
    private final User dean = User.builder().id(5L).emailAddress("dean@chnu.edu.ua").build();

    @Test
    void ac43_formLoginIsAuditedAndResetsTheFailureCounter() {
        when(userRepository.findByEmailAddressIgnoreCase("dean@chnu.edu.ua")).thenReturn(Optional.of(dean));

        listener.onLogin(new AuthenticationSuccessEvent(
            UsernamePasswordAuthenticationToken.authenticated("dean@chnu.edu.ua", "n/a", List.of())));

        assertThat(dean.getLastLoginAt()).isNotNull();
        verify(audit).record(AuditAction.LOGIN_SUCCESS, 5L);
        verify(attempts).reset("dean@chnu.edu.ua");
    }

    @Test
    void otherAuthenticationsAreIgnored() {
        listener.onLogin(new AuthenticationSuccessEvent(new TestingAuthenticationToken("x", "y")));

        verifyNoInteractions(userRepository, attempts, audit);
    }

    @Test
    void ac43_endOfALoginSessionIsAuditedAsLogout() {
        when(userRepository.findByEmailAddressIgnoreCase("dean@chnu.edu.ua")).thenReturn(Optional.of(dean));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
            UsernamePasswordAuthenticationToken.authenticated("dean@chnu.edu.ua", "n/a", List.of())));

        listener.onSessionEnd(new HttpSessionDestroyedEvent(session));

        verify(audit).record(AuditAction.LOGOUT, 5L);
    }
}
