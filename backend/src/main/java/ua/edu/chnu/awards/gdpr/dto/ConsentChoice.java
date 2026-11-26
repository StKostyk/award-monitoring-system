package ua.edu.chnu.awards.gdpr.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Grant or withdrawal of an optional consent.
 *
 * @param granted true to grant, false to withdraw
 */
public record ConsentChoice(@NotNull Boolean granted) {
}
