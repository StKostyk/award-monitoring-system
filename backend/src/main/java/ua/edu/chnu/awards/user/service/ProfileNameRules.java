package ua.edu.chnu.awards.user.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.auth.dto.RegisterRequest;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.FieldViolation;
import ua.edu.chnu.awards.user.dto.UserUpdateRequest;

/**
 * Checks a profile change against the name rules of registration.
 */
@Component
public class ProfileNameRules {

    static final int MAX_LENGTH = 100;
    private static final Pattern NAME = Pattern.compile(RegisterRequest.NAME);

    /**
     * Cleans the sent names and refuses the request when a name or another property is not acceptable.
     *
     * @param request the change
     * @return the trimmed names keyed by their column, only those that were sent
     * @throws ApiProblemException 422 {@code validation-failed} listing every refused field
     */
    public Map<String, String> check(UserUpdateRequest request) {
        List<FieldViolation> errors = new ArrayList<>();
        Map<String, String> names = new LinkedHashMap<>();
        accept("firstName", "first_name", request.getFirstName(), names, errors);
        accept("lastName", "last_name", request.getLastName(), names, errors);
        request.unknownProperties().forEach(name ->
            errors.add(new FieldViolation(name, "not-allowed", "This property cannot be changed here")));
        if (!errors.isEmpty()) {
            throw ApiProblemException.validationFailed("The profile change has invalid fields", errors);
        }
        return names;
    }

    private static void accept(String field, String column, String value, Map<String, String> names,
                               List<FieldViolation> errors) {
        if (value == null) {
            return;
        }
        String name = value.strip();
        if (name.isEmpty()) {
            errors.add(new FieldViolation(field, "required", "The name cannot be empty"));
        } else if (name.length() > MAX_LENGTH) {
            errors.add(new FieldViolation(field, "too-long", "At most " + MAX_LENGTH + " characters"));
        } else if (NAME.matcher(name).matches()) {
            names.put(column, name);
        } else {
            errors.add(new FieldViolation(field, "invalid",
                "Letters, apostrophes, hyphens and spaces only, starting with a letter"));
        }
    }
}
