package ua.edu.chnu.awards.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * An email address, for resend-style requests.
 *
 * @param email the address
 */
public record EmailRequest(@NotBlank @Email String email) {
}
