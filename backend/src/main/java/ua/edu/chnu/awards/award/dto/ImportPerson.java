package ua.edu.chnu.awards.award.dto;

import ua.edu.chnu.awards.user.entity.User;

/**
 * The person a row of an import belongs to.
 *
 * @param id             the user, null for a person the import adds
 * @param firstName      first name
 * @param lastName       surname
 * @param withoutAccount true for a person without an account, existing or added by the import
 */
public record ImportPerson(Long id, String firstName, String lastName, boolean withoutAccount) {

    /**
     * An existing user.
     *
     * @param user the user
     * @return the person
     */
    public static ImportPerson of(User user) {
        return new ImportPerson(user.getId(), user.getFirstName(), user.getLastName(), user.isWithoutAccount());
    }
}
