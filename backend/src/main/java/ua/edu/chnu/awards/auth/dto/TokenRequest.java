package ua.edu.chnu.awards.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A one-time token taken from an email link together with the password that goes with it: the registration
 * password when confirming an address, the new password when resetting it.
 *
 * @param token    the raw token
 * @param password the password
 */
public record TokenRequest(@NotBlank String token, @NotBlank @Size(max = 72) String password) {

    @Override
    public String toString() {
        return "TokenRequest[token=" + token + "]";
    }
}
