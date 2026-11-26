package ua.edu.chnu.awards.award.dto;

import ua.edu.chnu.awards.user.entity.User;

/**
 * A person named on an award: its owner or the actor of a version.
 *
 * @param id    user identifier
 * @param name  first and last name
 * @param email address
 */
public record UserRef(Long id, String name, String email) {

    /**
     * The reference to a user.
     *
     * @param user the user, or null
     * @return the reference, or null without a user
     */
    public static UserRef of(User user) {
        return user == null ? null : new UserRef(user.getId(), user.getFullName(), user.getEmailAddress());
    }
}
