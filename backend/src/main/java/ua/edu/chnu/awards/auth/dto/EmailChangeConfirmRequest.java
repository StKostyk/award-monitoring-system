package ua.edu.chnu.awards.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The token of an address change confirmation link.
 *
 * @param token the raw token from the link
 */
public record EmailChangeConfirmRequest(@NotBlank @Size(max = 100) String token) {

    @Override
    public String toString() {
        return "EmailChangeConfirmRequest[token=***]";
    }
}
