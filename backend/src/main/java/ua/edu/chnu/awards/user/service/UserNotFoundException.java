package ua.edu.chnu.awards.user.service;

import ua.edu.chnu.awards.common.web.NotFoundException;

/**
 * The referenced user does not exist.
 */
public class UserNotFoundException extends NotFoundException {

    private static final long serialVersionUID = 1L;

    public UserNotFoundException(long userId) {
        super("User " + userId + " not found");
    }
}
