package ua.edu.chnu.awards.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to sign in with another address from now on.
 *
 * @param newEmail        the institutional address to move to
 * @param currentPassword the caller's password, required so a borrowed session cannot move the account
 */
public record EmailChangeRequest(
    @NotBlank @Email @Size(max = 254) String newEmail,
    @NotBlank @Size(max = 72) String currentPassword) {

    @Override
    public String toString() {
        return "EmailChangeRequest[newEmail=" + newEmail + "]";
    }
}
