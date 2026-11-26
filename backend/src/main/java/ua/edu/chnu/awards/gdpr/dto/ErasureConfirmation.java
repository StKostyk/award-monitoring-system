package ua.edu.chnu.awards.gdpr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The password that confirms an erasure request.
 *
 * @param password the caller's current password
 */
public record ErasureConfirmation(@NotBlank @Size(max = 72) String password) {

    @Override
    public String toString() {
        return "ErasureConfirmation[]";
    }
}
