package ua.edu.chnu.awards.auth.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

class AccountStatusCheckerTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AccountStatusChecker checker = new AccountStatusChecker(userRepository);

    @Test
    void ac16_pendingAccountIsRefusedWithItsStatus() {
        when(userRepository.findByEmailAddressIgnoreCase("p@chnu.edu.ua"))
            .thenReturn(Optional.of(User.builder().accountStatus(AccountStatus.PENDING).build()));

        assertThatThrownBy(() -> checker.check(details("p@chnu.edu.ua")))
            .isInstanceOf(AccountStatusRefusedException.class)
            .extracting(e -> ((AccountStatusRefusedException) e).getStatus())
            .isEqualTo(AccountStatus.PENDING);
    }

    @Test
    void ac16_activeAndRetiredAccountsPass() {
        when(userRepository.findByEmailAddressIgnoreCase("a@chnu.edu.ua"))
            .thenReturn(Optional.of(User.builder().accountStatus(AccountStatus.ACTIVE).build()));
        when(userRepository.findByEmailAddressIgnoreCase("r@chnu.edu.ua"))
            .thenReturn(Optional.of(User.builder().accountStatus(AccountStatus.RETIRED).build()));
        when(userRepository.findByEmailAddressIgnoreCase("ghost@chnu.edu.ua")).thenReturn(Optional.empty());

        assertThatCode(() -> checker.check(details("a@chnu.edu.ua"))).doesNotThrowAnyException();
        assertThatCode(() -> checker.check(details("r@chnu.edu.ua"))).doesNotThrowAnyException();
        assertThatCode(() -> checker.check(details("ghost@chnu.edu.ua"))).doesNotThrowAnyException();
    }

    private static UserDetails details(String email) {
        return org.springframework.security.core.userdetails.User.withUsername(email)
            .password("x").authorities(List.of()).build();
    }
}
