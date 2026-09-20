package ua.edu.chnu.awards.auth.dto;

import ua.edu.chnu.awards.user.entity.AccountStatus;

/**
 * Outcome of a registration or verification step.
 *
 * @param email  the address concerned
 * @param status account status after the step
 */
public record RegistrationResponse(String email, AccountStatus status) {
}
