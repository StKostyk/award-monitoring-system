package ua.edu.chnu.awards.award.service;

import java.util.Optional;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.service.UserAccounts;

import lombok.RequiredArgsConstructor;

/**
 * Finds a person a faculty secretary or dean added, within the caller's scope; any other account answers as
 * unknown.
 */
@Component
@RequiredArgsConstructor
public class PeopleLookup {

    private final UserAccounts users;
    private final PersonRules rules;
    private final RecipientUnits units;

    /**
     * The person, read without a lock.
     *
     * @param id the person
     * @return the person
     * @throws PersonNotFoundException 404 when unknown, never added, or outside the caller's scope
     */
    public User inScope(long id) {
        rules.requireRole();
        return visible(users.findById(id), id);
    }

    /**
     * The person with the row locked until the transaction ends.
     *
     * @param id the person
     * @return the person
     * @throws PersonNotFoundException 404 when unknown, never added, or outside the caller's scope
     */
    public User lockedInScope(long id) {
        rules.requireRole();
        return visible(users.findByIdForUpdate(id), id);
    }

    private User visible(Optional<User> found, long id) {
        return found.filter(person -> person.getAddedById() != null)
            .filter(person -> person.getAccountStatus() != AccountStatus.DELETED)
            .filter(person -> units.subtree().contains(person.getOrganization().getId()))
            .orElseThrow(() -> new PersonNotFoundException(id));
    }
}
