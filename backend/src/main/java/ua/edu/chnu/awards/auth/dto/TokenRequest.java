package ua.edu.chnu.awards.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * A one-time token taken from an email link.
 *
 * @param token the raw token
 */
public record TokenRequest(@NotBlank String token) {
}
