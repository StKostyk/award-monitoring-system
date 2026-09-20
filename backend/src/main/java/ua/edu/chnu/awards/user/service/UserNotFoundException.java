package ua.edu.chnu.awards.user.service;

/**
 * The referenced user does not exist.
 */
public class UserNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UserNotFoundException(long userId) {
        super("User " + userId + " not found");
    }
}
