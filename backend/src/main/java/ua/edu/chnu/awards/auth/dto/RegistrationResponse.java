package ua.edu.chnu.awards.auth.dto;

import ua.edu.chnu.awards.user.entity.AccountStatus;

/**
 * Outcome of a registration or verification step.
 *
 * @param email         the address concerned
 * @param status        account status after the step
 * @param waitingAwards awards entered for the person before the account was claimed, 0 otherwise
 */
public record RegistrationResponse(String email, AccountStatus status, long waitingAwards) {

    /**
     * An outcome without waiting awards.
     *
     * @param email  the address concerned
     * @param status account status after the step
     */
    public RegistrationResponse(String email, AccountStatus status) {
        this(email, status, 0);
    }
}
