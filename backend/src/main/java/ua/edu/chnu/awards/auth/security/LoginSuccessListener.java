package ua.edu.chnu.awards.auth.security;

import java.time.Instant;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Records the time of the last successful form login.
 */
@Component
@RequiredArgsConstructor
public class LoginSuccessListener {

    private final UserRepository userRepository;

    @EventListener
    @Transactional
    public void onLogin(AuthenticationSuccessEvent event) {
        if (!(event.getAuthentication() instanceof UsernamePasswordAuthenticationToken)) {
            return;
        }
        userRepository.findByEmailAddressIgnoreCase(event.getAuthentication().getName())
            .ifPresent(user -> user.setLastLoginAt(Instant.now()));
    }
}
