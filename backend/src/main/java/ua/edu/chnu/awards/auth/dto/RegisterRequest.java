package ua.edu.chnu.awards.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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
    @NotBlank @Size(max = 100) @Pattern(regexp = NAME) String firstName,
    @NotBlank @Size(max = 100) @Pattern(regexp = NAME) String lastName,
    @NotNull Long organizationId) {

    /** Letters, apostrophes, hyphens and spaces, starting with a letter. */
    public static final String NAME = "^\\p{L}[\\p{L}'’\\- ]*$";

    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", firstName=" + firstName + ", lastName=" + lastName
            + ", organizationId=" + organizationId + "]";
    }
}
