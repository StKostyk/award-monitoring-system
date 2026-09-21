package ua.edu.chnu.awards.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.auth.service.DeviceService;
import ua.edu.chnu.awards.auth.service.LoginAttemptService;
import ua.edu.chnu.awards.common.web.ClientRequest;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

class LoginSuccessListenerTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final LoginAttemptService attempts = mock(LoginAttemptService.class);
    private final AuditService audit = mock(AuditService.class);
    private final DeviceService devices = mock(DeviceService.class);
    private final LoginSuccessListener listener = new LoginSuccessListener(userRepository, attempts, audit, devices);
    private final User dean = User.builder().id(5L).emailAddress("dean@chnu.edu.ua").build();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void ac43_ac51_formLoginIsAuditedResetsTheFailureCounterAndRecordsTheDevice() {
        when(userRepository.findByEmailAddressIgnoreCase("dean@chnu.edu.ua")).thenReturn(Optional.of(dean));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("User-Agent", "Mozilla/5.0 Firefox/130.0");
        request.addHeader("Accept-Language", "uk");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        listener.onLogin(new AuthenticationSuccessEvent(
            UsernamePasswordAuthenticationToken.authenticated("dean@chnu.edu.ua", "n/a", List.of())));

        assertThat(dean.getLastLoginAt()).isNotNull();
        verify(audit).record(AuditAction.LOGIN_SUCCESS, 5L);
        verify(attempts).reset("dean@chnu.edu.ua");
        verify(devices).recordSignIn(dean, new ClientRequest("203.0.113.7", "Mozilla/5.0 Firefox/130.0", "uk", null));
    }

    @Test
    void ac51_aFailedDeviceRecordDoesNotBreakTheLogin() {
        when(userRepository.findByEmailAddressIgnoreCase("dean@chnu.edu.ua")).thenReturn(Optional.of(dean));
        doThrow(new DataIntegrityViolationException("uk_user_devices_fingerprint"))
            .when(devices).recordSignIn(any(), any());

        listener.onLogin(new AuthenticationSuccessEvent(
            UsernamePasswordAuthenticationToken.authenticated("dean@chnu.edu.ua", "n/a", List.of())));

        verify(audit).record(AuditAction.LOGIN_SUCCESS, 5L);
        verify(attempts).reset("dean@chnu.edu.ua");
    }

    @Test
    void otherAuthenticationsAreIgnored() {
        listener.onLogin(new AuthenticationSuccessEvent(new TestingAuthenticationToken("x", "y")));

        verifyNoInteractions(userRepository, attempts, audit, devices);
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
