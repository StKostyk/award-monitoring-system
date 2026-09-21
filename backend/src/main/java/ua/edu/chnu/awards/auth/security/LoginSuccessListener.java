package ua.edu.chnu.awards.auth.security;

import java.time.Instant;

import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.auth.service.DeviceService;
import ua.edu.chnu.awards.auth.service.LoginAttemptService;
import ua.edu.chnu.awards.common.web.ClientRequest;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Records successful form logins (last login time, audit trail, failure counter reset, known device) and the
 * end of login sessions.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoginSuccessListener {

    private final UserRepository userRepository;
    private final LoginAttemptService attempts;
    private final AuditService audit;
    private final DeviceService devices;

    @EventListener
    @Transactional
    public void onLogin(AuthenticationSuccessEvent event) {
        if (!(event.getAuthentication() instanceof UsernamePasswordAuthenticationToken)) {
            return;
        }
        String email = event.getAuthentication().getName();
        ClientRequest client = ClientRequest.current();
        userRepository.findByEmailAddressIgnoreCase(email).ifPresent(user -> {
            user.setLastLoginAt(Instant.now());
            audit.record(AuditAction.LOGIN_SUCCESS, user.getId());
            try {
                devices.recordSignIn(user, client);
            } catch (DataAccessException e) {
                log.error("Device of user {} could not be recorded", user.getId(), e);
            }
        });
        attempts.reset(email);
    }

    @EventListener
    @Transactional
    public void onSessionEnd(HttpSessionDestroyedEvent event) {
        for (SecurityContext context : event.getSecurityContexts()) {
            if (context.getAuthentication() == null) {
                continue;
            }
            Long userId = userRepository.findByEmailAddressIgnoreCase(context.getAuthentication().getName())
                .map(User::getId).orElse(null);
            audit.record(AuditAction.LOGOUT, userId);
        }
    }
}
