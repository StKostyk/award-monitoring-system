package ua.edu.chnu.awards.user.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Reads and saves user accounts for other modules.
 */
@Service
@RequiredArgsConstructor
public class UserAccounts {

    private final UserRepository users;

    /**
     * The account with the id.
     *
     * @param id the account
     * @return the account, empty when unknown
     */
    public Optional<User> findById(Long id) {
        return users.findById(id);
    }

    /**
     * The account with the id, locked for the rest of the transaction.
     *
     * @param id the account
     * @return the account, empty when unknown
     */
    public Optional<User> findByIdForUpdate(Long id) {
        return users.findByIdForUpdate(id);
    }

    /**
     * The accounts with the ids.
     *
     * @param ids the accounts
     * @return the accounts found
     */
    public List<User> findAllById(Iterable<Long> ids) {
        return users.findAllById(ids);
    }

    /**
     * A reference to the account, for mapping a relation without reading it.
     *
     * @param id the account
     * @return the reference
     */
    public User getReferenceById(Long id) {
        return users.getReferenceById(id);
    }

    /**
     * The account with the address, ignoring case.
     *
     * @param emailAddress the sign-in address
     * @return the account, empty when unknown
     */
    public Optional<User> findByEmailAddressIgnoreCase(String emailAddress) {
        return users.findByEmailAddressIgnoreCase(emailAddress);
    }

    /**
     * Whether an account uses the address, ignoring case.
     *
     * @param emailAddress the sign-in address
     * @return true when taken
     */
    public boolean existsByEmailAddressIgnoreCase(String emailAddress) {
        return users.existsByEmailAddressIgnoreCase(emailAddress);
    }

    /**
     * Saves the account and writes it at once.
     *
     * @param user the account
     * @return the saved account
     */
    public User saveAndFlush(User user) {
        return users.saveAndFlush(user);
    }

    /**
     * Records a successful sign-in.
     *
     * @param id the account
     * @param at when it happened
     * @return the number of updated rows
     */
    public int recordLogin(Long id, Instant at) {
        return users.recordLogin(id, at);
    }
}
