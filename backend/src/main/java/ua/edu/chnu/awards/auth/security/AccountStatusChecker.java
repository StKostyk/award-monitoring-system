package ua.edu.chnu.awards.auth.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.service.UserAccounts;

import lombok.RequiredArgsConstructor;

/**
 * Refuses sign-in for accounts whose status forbids it, before the password is checked.
 */
@Component
@RequiredArgsConstructor
public class AccountStatusChecker implements UserDetailsChecker {

    private final UserAccounts users;

    @Override
    @Transactional(readOnly = true)
    public void check(UserDetails user) {
        AccountStatus status = users.findByEmailAddressIgnoreCase(user.getUsername())
            .map(User::getAccountStatus)
            .orElse(AccountStatus.ACTIVE);
        if (!status.canLogIn()) {
            throw new AccountStatusRefusedException(status);
        }
    }
}
