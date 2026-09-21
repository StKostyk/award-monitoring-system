package ua.edu.chnu.awards.auth.security;

import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.auth.service.LoginAttemptService;

import lombok.RequiredArgsConstructor;

/**
 * Refuses a locked account before the password is checked, so correct credentials do not end the lock.
 */
@Component
@RequiredArgsConstructor
public class LockedAccountChecker implements UserDetailsChecker {

    private final LoginAttemptService attempts;

    @Override
    public void check(UserDetails user) {
        if (attempts.isLocked(user.getUsername())) {
            throw new LockedException("Account is temporarily locked");
        }
    }
}
