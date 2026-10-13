package ua.edu.chnu.awards.user;

import ua.edu.chnu.awards.user.entity.User;

/**
 * Ends what an account is signed in with; implemented by the module that issues sessions and tokens.
 */
public interface AccountSessions {

    /**
     * Signs the account out everywhere: its login sessions, authorizations and access tokens stop working.
     *
     * @param user the account
     */
    void signOutEverywhere(User user);
}
