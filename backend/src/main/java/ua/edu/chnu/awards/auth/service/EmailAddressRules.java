package ua.edu.chnu.awards.auth.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.config.AuthProperties;

import lombok.RequiredArgsConstructor;

/**
 * Rules for an address that is to become a sign-in address, shared by registration and the address change, so
 * both answer with the same problem types.
 */
@Component
@RequiredArgsConstructor
public class EmailAddressRules {

    private final AuthProperties properties;

    /**
     * Refuses an address outside the institutional domains.
     *
     * @param email  a normalised address
     * @param detail the explanation shown to the caller
     * @throws ApiProblemException 422 {@code institutional-email-required}
     */
    public void requireInstitutional(String email, String detail) {
        if (!properties.isInstitutional(email)) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "institutional-email-required", detail);
        }
    }

    /**
     * The problem for an address that already belongs to an account.
     *
     * @param cause the constraint violation that revealed it, null when a lookup did
     * @return 409 {@code email-taken}
     */
    public ApiProblemException taken(Throwable cause) {
        return new ApiProblemException(HttpStatus.CONFLICT, "email-taken",
            "An account with this address already exists", cause);
    }
}
