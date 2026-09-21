package ua.edu.chnu.awards.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The token from a "this was not me" link.
 *
 * @param token the raw token
 */
public record RevokeRequest(@NotBlank String token) {
}
