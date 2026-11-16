package ua.edu.chnu.awards.award.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.award.dto.PersonForm;
import ua.edu.chnu.awards.common.EmailUtils;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.FieldViolation;
import ua.edu.chnu.awards.organization.OrganizationTree;
import ua.edu.chnu.awards.organization.entity.OrganizationType;
import ua.edu.chnu.awards.user.dto.NameConstants;
import ua.edu.chnu.awards.user.entity.User;

/**
 * Checks for adding and changing a person without an account: the caller's role and scope, the names, the
 * address, and the people of the same name already in the department.
 */
@Component
public class PersonRules {

    static final String ORGANIZATION = "organizationId";
    static final String EMAIL = "email";
    private static final int NAME_MAX = 100;
    private static final Pattern NAME = Pattern.compile(NameConstants.NAME);
    private static final Pattern APOSTROPHES = Pattern.compile("['’ʼ`]");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern SINGLE_ADDRESS = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+");

    private final RecipientUnits units;
    private final OrganizationTree tree;
    private final List<String> domains;

    /**
     * Creates the rules.
     *
     * @param units   the caller's faculty secretary and dean scopes
     * @param tree    the organisation tree
     * @param domains the institutional address domains
     */
    public PersonRules(RecipientUnits units, OrganizationTree tree,
                       @Value("${app.auth.allowed-email-domains:chnu.edu.ua}") List<String> domains) {
        this.units = units;
        this.tree = tree;
        this.domains = List.copyOf(domains);
    }

    /**
     * Refuses a caller without a faculty secretary or dean role, own or lent.
     *
     * @throws AccessDeniedException 403 {@code access-denied}
     */
    public void requireRole() {
        if (!units.held()) {
            throw new AccessDeniedException("Only a faculty secretary or dean can manage people without an account");
        }
    }

    /**
     * Whether the department lies inside the caller's scope.
     *
     * @param organizationId the department
     * @return true for an active department the caller covers
     */
    public boolean covers(long organizationId) {
        return units.covers(organizationId) && tree.node(organizationId)
            .filter(node -> node.type() == OrganizationType.DEPARTMENT).isPresent();
    }

    /**
     * Cleans and checks the form: trimmed names, a normalised address, a department inside the scope.
     *
     * @param form         the form as sent
     * @param emailOptional false when the person already has an address that may not be removed
     * @return the cleaned form
     * @throws ApiProblemException 422 {@code validation-failed} listing every refused field
     */
    public PersonForm check(PersonForm form, boolean emailOptional) {
        List<FieldViolation> errors = new ArrayList<>();
        final String firstName = name(errors, "firstName", form.firstName());
        final String lastName = name(errors, "lastName", form.lastName());
        String email = email(errors, form.email(), emailOptional);
        department(errors, form.organizationId());
        if (!errors.isEmpty()) {
            throw ApiProblemException.validationFailed("The person form has invalid fields", errors);
        }
        return new PersonForm(firstName, lastName, form.organizationId(), email, form.duplicateAcknowledged(),
            form.version());
    }

    /**
     * The people of the department whose name matches, ignoring case, apostrophes and surrounding spaces.
     *
     * @param form   the cleaned form
     * @param people the people without an account of the form's department
     * @return the matches
     */
    public List<User> sameName(PersonForm form, List<User> people) {
        String first = comparable(form.firstName());
        String last = comparable(form.lastName());
        return people.stream()
            .filter(person -> comparable(person.getFirstName()).equals(first)
                && comparable(person.getLastName()).equals(last))
            .toList();
    }

    /**
     * The problem for a stale version.
     *
     * @param current the version stored now
     * @return 409 {@code version-conflict}
     */
    public static ApiProblemException stale(Long current) {
        return new ApiProblemException(HttpStatus.CONFLICT, "version-conflict",
            "The person was changed in the meantime", Map.of("currentVersion", current));
    }

    /**
     * An address with only its first character and the domain visible.
     *
     * @param email the address, null for none
     * @return the masked address, null for none
     */
    public static String mask(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }

    static String comparable(String name) {
        String plain = Normalizer.normalize(name, Normalizer.Form.NFC).strip().toLowerCase(Locale.ROOT);
        return SPACES.matcher(APOSTROPHES.matcher(plain).replaceAll("")).replaceAll(" ");
    }

    private boolean isInstitutional(String email) {
        String domain = email.substring(email.lastIndexOf('@') + 1);
        return SINGLE_ADDRESS.matcher(email).matches() && domains.stream().anyMatch(domain::equalsIgnoreCase);
    }

    private String email(List<FieldViolation> errors, String value, boolean optional) {
        String email = value == null || value.isBlank() ? null : EmailUtils.normalize(value);
        if (email == null && !optional) {
            errors.add(new FieldViolation(EMAIL, "required", "An address once entered cannot be removed"));
        } else if (email != null && !isInstitutional(email)) {
            errors.add(new FieldViolation(EMAIL, "institutional", "Only a university address can be entered"));
        }
        return email;
    }

    private void department(List<FieldViolation> errors, Long organizationId) {
        if (organizationId == null) {
            errors.add(new FieldViolation(ORGANIZATION, "required", "A department is required"));
        } else if (!covers(organizationId)) {
            errors.add(new FieldViolation(ORGANIZATION, "out-of-scope",
                "People can be added only to an active department of your faculty secretary or dean role"));
        }
    }

    private static String name(List<FieldViolation> errors, String field, String value) {
        String trimmed = value == null ? "" : value.strip();
        if (trimmed.isEmpty()) {
            errors.add(new FieldViolation(field, "required", "The name is required"));
        } else if (trimmed.length() > NAME_MAX) {
            errors.add(new FieldViolation(field, "too-long", "At most 100 characters"));
        } else if (!NAME.matcher(trimmed).matches()) {
            errors.add(new FieldViolation(field, "invalid", "Letters, apostrophes, hyphens and spaces only"));
        }
        return trimmed;
    }
}
