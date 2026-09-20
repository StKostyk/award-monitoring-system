package ua.edu.chnu.awards.auth.security;

import org.springframework.security.authentication.AccountStatusException;

import ua.edu.chnu.awards.user.entity.AccountStatus;

/**
 * Thrown when the account exists but its status does not allow signing in.
 */
public class AccountStatusRefusedException extends AccountStatusException {

    private static final long serialVersionUID = 1L;

    private final AccountStatus status;

    public AccountStatusRefusedException(AccountStatus status) {
        super("Account status " + status + " does not allow signing in");
        this.status = status;
    }

    public AccountStatus getStatus() {
        return status;
    }
}
