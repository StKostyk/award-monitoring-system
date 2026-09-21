package ua.edu.chnu.awards.auth.security;

import java.time.Instant;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.auth.service.LoginAttemptService;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Records successful form logins (last login time, audit trail, failure counter reset) and the end of login
 * sessions.
 */
@Component
@RequiredArgsConstructor
public class LoginSuccessListener {

    private final UserRepository userRepository;
    private final LoginAttemptService attempts;
    private final AuditService audit;

    @EventListener
    @Transactional
    public void onLogin(AuthenticationSuccessEvent event) {
        if (!(event.getAuthentication() instanceof UsernamePasswordAuthenticationToken)) {
            return;
        }
        String email = event.getAuthentication().getName();
        userRepository.findByEmailAddressIgnoreCase(email).ifPresent(user -> {
            user.setLastLoginAt(Instant.now());
            audit.record(AuditAction.LOGIN_SUCCESS, user.getId());
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
