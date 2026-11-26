package ua.edu.chnu.awards.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.common.SecretUtils;
import ua.edu.chnu.awards.common.erasure.ErasureContext;
import ua.edu.chnu.awards.common.erasure.ErasureContributor;
import ua.edu.chnu.awards.common.erasure.ErasureOrderConstants;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Erasure of the account row: it stays, so records kept under the pseudonym still have an owner, but carries
 * the pseudonym, an address that cannot receive mail, a password nobody knows and the status {@code DELETED}.
 */
@Component
@RequiredArgsConstructor
public class AccountErasure implements ErasureContributor {

    private static final int SECRET_BYTES = 32;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    @Override
    public int order() {
        return ErasureOrderConstants.ACCOUNT;
    }

    @Override
    public void erase(ErasureContext context) {
        User user = users.findById(context.userId()).orElseThrow(() -> new UserNotFoundException(context.userId()));
        user.setFirstName(ErasureContext.FIRST_NAME);
        user.setLastName(context.lastName());
        user.setEmailAddress(context.erasedEmailAddress());
        user.setPasswordHash(passwordEncoder.encode(SecretUtils.randomSecret(SECRET_BYTES)));
        user.setAccountStatus(AccountStatus.DELETED);
        user.setLastLoginAt(null);
        users.saveAndFlush(user);
        context.count("accountScrubbed", 1);
    }
}
