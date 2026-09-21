package ua.edu.chnu.awards.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * An email address, for resend-style requests.
 *
 * @param email the address
 */
public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {
}
