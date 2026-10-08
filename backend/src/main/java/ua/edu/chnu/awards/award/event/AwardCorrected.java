package ua.edu.chnu.awards.award.event;

import java.util.List;

/**
 * A reviewer corrected a pending award; its owner is told once the surrounding transaction commits.
 *
 * @param email     the owner's address
 * @param firstName used in the greeting
 * @param awardId   the award
 * @param title     the award's title, the Ukrainian one when it has no English title
 * @param titleUk   the award's Ukrainian title, the English one when it has none
 * @param reviewer  full name of the reviewer
 * @param reason    why the reviewer corrected the award
 * @param changes   the corrected fields with their old and new values
 */
public record AwardCorrected(String email, String firstName, long awardId, String title, String titleUk,
                             String reviewer, String reason, List<Change> changes) {

    /**
     * Keeps an unmodifiable copy of the changes.
     */
    public AwardCorrected {
        changes = List.copyOf(changes);
    }

    /**
     * One corrected field; the values differ by language only for the category.
     *
     * @param field  snapshot field name
     * @param from   old value in English, null when empty
     * @param to     new value in English, null when empty
     * @param fromUk old value in Ukrainian, null when empty
     * @param toUk   new value in Ukrainian, null when empty
     */
    public record Change(String field, String from, String to, String fromUk, String toUk) {
    }
}
