package ua.edu.chnu.awards.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import ua.edu.chnu.awards.user.dto.NameConstants;

/**
 * Self-registration of a university employee.
 *
 * @param email          institutional address
 * @param password       chosen password
 * @param firstName      first name
 * @param lastName       last name
 * @param organizationId department the person belongs to
 */
public record RegisterRequest(
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(max = 72) String password,
    @NotBlank @Size(max = 100) @Pattern(regexp = NameConstants.NAME) String firstName,
    @NotBlank @Size(max = 100) @Pattern(regexp = NameConstants.NAME) String lastName,
    @NotNull Long organizationId) {

    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", firstName=" + firstName + ", lastName=" + lastName
            + ", organizationId=" + organizationId + "]";
    }
}
