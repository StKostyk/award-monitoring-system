package ua.edu.chnu.awards.auth.dto;

import java.text.Normalizer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import ua.edu.chnu.awards.user.dto.NameConstants;

/**
 * Self-registration of a university employee.
 *
 * @param email                institutional address
 * @param password             chosen password
 * @param firstName            first name
 * @param lastName             last name
 * @param organizationId       department the person belongs to
 * @param privacyNoticeVersion privacy notice version the person acknowledged
 */
public record RegisterRequest(
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(max = 72) String password,
    @NotBlank @Size(max = 100) @Pattern(regexp = NameConstants.NAME) String firstName,
    @NotBlank @Size(max = 100) @Pattern(regexp = NameConstants.NAME) String lastName,
    @NotNull Long organizationId,
    @NotBlank @Size(max = 20) String privacyNoticeVersion) {

    /**
     * Keeps the names in composed (NFC) form, so a letter typed with a combining mark passes the name rules.
     */
    public RegisterRequest {
        firstName = composed(firstName);
        lastName = composed(lastName);
    }

    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", firstName=" + firstName + ", lastName=" + lastName
            + ", organizationId=" + organizationId + ", privacyNoticeVersion=" + privacyNoticeVersion + "]";
    }

    private static String composed(String name) {
        return name == null ? null : Normalizer.normalize(name, Normalizer.Form.NFC);
    }
}
