package ua.edu.chnu.awards.auth.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import ua.edu.chnu.awards.auth.service.LoginAttemptService;

class LockedAccountCheckerTest {

    private final LoginAttemptService attempts = mock(LoginAttemptService.class);
    private final LockedAccountChecker checker = new LockedAccountChecker(attempts);
    private final UserDetails dean = User.withUsername("dean@chnu.edu.ua").password("x").authorities("ROLE_X").build();

    @Test
    void ac41_lockedAccountIsRefusedBeforeThePasswordCheck() {
        when(attempts.isLocked("dean@chnu.edu.ua")).thenReturn(true);

        assertThatThrownBy(() -> checker.check(dean)).isInstanceOf(LockedException.class);
    }

    @Test
    void ac41_unlockedAccountPasses() {
        when(attempts.isLocked("dean@chnu.edu.ua")).thenReturn(false);

        assertThatCode(() -> checker.check(dean)).doesNotThrowAnyException();
    }
}
