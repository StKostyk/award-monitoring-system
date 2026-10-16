package ua.edu.chnu.awards.gdpr.dto;

/**
 * Why a legal hold is placed or lifted.
 *
 * @param reason 10 to 500 characters after trimming
 */
public record LegalHoldReason(String reason) {
}
