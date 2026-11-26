package ua.edu.chnu.awards.gdpr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Acknowledgement of a privacy notice version.
 *
 * @param version the version the person read
 */
public record NoticeAcknowledgement(@NotBlank @Size(max = 20) String version) {
}
