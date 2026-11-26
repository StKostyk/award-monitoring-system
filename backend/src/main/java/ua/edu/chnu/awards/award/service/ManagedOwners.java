package ua.edu.chnu.awards.award.service;

import java.util.Optional;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.service.UserAccounts;

import lombok.RequiredArgsConstructor;

/**
 * The people whose awards the caller manages in their place: people without an account of their own, in a faculty
 * or department of the caller's faculty secretary or dean role.
 */
@Component
@RequiredArgsConstructor
public class ManagedOwners {

    private final UserAccounts users;
    private final RecipientUnits units;

    /**
     * Whether the caller manages the awards of the owner.
     *
     * @param owner the owner of an award
     * @return true for a person without an account inside the caller's scope
     */
    public boolean manages(User owner) {
        return owner.isWithoutAccount() && owner.getAccountStatus() != AccountStatus.DELETED
            && units.covers(owner.getOrganization().getId());
    }

    /**
     * A person the caller may enter an award for.
     *
     * @param id the person
     * @return the person, empty when unknown, with an account of their own or outside the caller's scope
     */
    public Optional<User> find(long id) {
        return users.findById(id).filter(this::manages);
    }
}
