package ua.edu.chnu.awards.auth.security;

import java.util.List;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.user.service.UserAccounts;

import lombok.RequiredArgsConstructor;

/**
 * Loads users by email address for form login. The principal carries the address and password hash only;
 * roles are resolved when tokens are issued.
 */
@Service
@RequiredArgsConstructor
public class JpaUserDetailsService implements UserDetailsService {

    private final UserAccounts users;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return users.findByEmailAddressIgnoreCase(username)
            .map(user -> User.withUsername(user.getEmailAddress())
                .password(user.getPasswordHash())
                .authorities(List.of())
                .build())
            .orElseThrow(() -> new UsernameNotFoundException("No user with address " + username));
    }
}
