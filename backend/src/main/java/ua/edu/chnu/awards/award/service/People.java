package ua.edu.chnu.awards.award.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.dto.Person;
import ua.edu.chnu.awards.award.dto.PersonDetail;
import ua.edu.chnu.awards.award.dto.PersonForm;
import ua.edu.chnu.awards.common.SecretUtils;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.FieldViolation;
import ua.edu.chnu.awards.organization.service.OrganizationLookup;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.service.UserAccounts;

import lombok.RequiredArgsConstructor;

/**
 * People without an account: a faculty secretary or dean adds a colleague who has not registered, so awards can
 * be entered for them, and changes or lists them within the role's scope.
 */
@Service
@RequiredArgsConstructor
public class People {

    private static final int SECRET_BYTES = 32;
    private static final int PAGE_MAX = 100;

    private final UserAccounts users;
    private final PersonRules rules;
    private final PeopleLookup lookup;
    private final PersonInvitations invitations;
    private final PersonViews views;
    private final RecipientUnits units;
    private final OrganizationLookup organizations;
    private final AccessScope access;
    private final AuditService audit;
    private final PasswordEncoder passwordEncoder;

    /**
     * Adds a person without an account; with an address the invitation goes once the transaction commits.
     *
     * @param form the form
     * @return the person
     * @throws ApiProblemException 409 {@code email-taken} or {@code possible-duplicate}, 422 on invalid fields
     */
    @Transactional
    public Person add(PersonForm form) {
        rules.requireRole();
        PersonForm clean = rules.check(form, true);
        requireFree(clean.email());
        if (!clean.acknowledged()) {
            List<User> matches = rules.sameName(clean, users.findWithoutAccount(clean.organizationId()));
            if (!matches.isEmpty()) {
                throw new ApiProblemException(HttpStatus.CONFLICT, "possible-duplicate",
                    "A person of the same name was already added to the department",
                    Map.of("matches", views.masked(matches)));
            }
        }
        User person = save(User.builder()
            .emailAddress(clean.email() == null ? User.placeholderAddress() : clean.email())
            .firstName(clean.firstName())
            .lastName(clean.lastName())
            .passwordHash(passwordEncoder.encode(SecretUtils.randomSecret(SECRET_BYTES)))
            .accountStatus(AccountStatus.UNCLAIMED)
            .organization(organizations.findById(clean.organizationId()).orElseThrow())
            .addedById(access.callerId())
            .build());
        audit.record(AuditAction.PERSON_ADDED, AuditEntityConstants.USER, access.callerId(), person.getId(),
            Map.of("organizationId", clean.organizationId()));
        if (person.hasMailbox()) {
            invitations.send(person);
            person = save(person);
        }
        return views.person(person);
    }

    /**
     * Changes the name, address or department; a first or new address receives the invitation.
     *
     * @param id   the person
     * @param form the form with the version last read
     * @return the person as it is now
     * @throws ApiProblemException 409 {@code version-conflict}, {@code person-claimed} or {@code email-taken},
     *                             422 on invalid fields
     */
    @Transactional
    public Person change(long id, PersonForm form) {
        User person = lookup.lockedInScope(id);
        if (person.getAccountStatus() != AccountStatus.UNCLAIMED) {
            throw PersonInvitations.claimed();
        }
        requireVersion(person, form.version());
        PersonForm clean = rules.check(form, !person.hasMailbox());
        List<String> changed = apply(person, clean);
        if (changed.isEmpty()) {
            return views.person(person);
        }
        User saved = save(person);
        audit.record(AuditAction.PERSON_CHANGED, AuditEntityConstants.USER, access.callerId(), id,
            Map.of("fields", changed));
        return views.person(saved);
    }

    /**
     * The person with their awards.
     *
     * @param id the person
     * @return the person
     * @throws PersonNotFoundException 404 as well once the person has an account of their own
     */
    @Transactional(readOnly = true)
    public PersonDetail get(long id) {
        User person = lookup.inScope(id);
        if (!person.isWithoutAccount()) {
            throw new PersonNotFoundException(id);
        }
        return views.detail(person);
    }

    /**
     * A page of people without an account in the caller's scope, by last and first name, addresses masked.
     *
     * @param q            a fragment of the name or address
     * @param organization only this department, null for every one in scope
     * @param page         0-based page
     * @param size         page size, capped at 100
     * @return the page
     */
    @Transactional(readOnly = true)
    public Page<Person> list(String q, Long organization, int page, int size) {
        rules.requireRole();
        Set<Long> scope = units.subtree();
        Set<Long> chosen = organization == null ? scope
            : scope.stream().filter(organization::equals).collect(Collectors.toSet());
        Page<User> found = users.findWithoutAccount(chosen, q, PageRequest.of(Math.max(page, 0),
            Math.clamp(size, 1, PAGE_MAX), Sort.by("lastName", "firstName", "id")));
        return new PageImpl<>(views.masked(found.getContent()), found.getPageable(), found.getTotalElements());
    }

    private List<String> apply(User person, PersonForm clean) {
        List<String> changed = new ArrayList<>();
        if (!clean.firstName().equals(person.getFirstName())) {
            changed.add("firstName");
            person.setFirstName(clean.firstName());
        }
        if (!clean.lastName().equals(person.getLastName())) {
            changed.add("lastName");
            person.setLastName(clean.lastName());
        }
        if (!clean.organizationId().equals(person.getOrganization().getId())) {
            changed.add(PersonRules.ORGANIZATION);
            person.setOrganization(organizations.findById(clean.organizationId()).orElseThrow());
        }
        if (clean.email() != null && !clean.email().equals(person.getEmailAddress())) {
            requireFree(clean.email());
            changed.add(PersonRules.EMAIL);
            person.setEmailAddress(clean.email());
            invitations.send(person);
        }
        return changed;
    }

    static void requireVersion(User person, Long version) {
        if (version == null) {
            throw ApiProblemException.validationFailed("The version last read is required",
                List.of(new FieldViolation("version", "required", "The version last read is required")));
        }
        if (!version.equals(person.getVersion())) {
            throw PersonRules.stale(person.getVersion());
        }
    }

    private void requireFree(String email) {
        if (email != null && users.existsByEmailAddressIgnoreCase(email)) {
            throw taken(null);
        }
    }

    private User save(User person) {
        try {
            return users.saveAndFlush(person);
        } catch (DataIntegrityViolationException e) {
            throw taken(e);
        }
    }

    private static ApiProblemException taken(Throwable cause) {
        return new ApiProblemException(HttpStatus.CONFLICT, "email-taken",
            "An account or person with this address already exists", cause);
    }
}
