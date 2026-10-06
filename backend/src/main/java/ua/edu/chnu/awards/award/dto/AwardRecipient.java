package ua.edu.chnu.awards.award.dto;

import ua.edu.chnu.awards.user.entity.Organization;

/**
 * Who received an award: its owner, or a faculty or department.
 *
 * @param type         person or unit
 * @param organization the unit, null for a person
 */
public record AwardRecipient(RecipientType type, UnitRef organization) {

    /** The owner received the award. */
    public static final AwardRecipient PERSON = new AwardRecipient(RecipientType.PERSON, null);

    /**
     * A unit received the award.
     *
     * @param unit the faculty or department
     * @return recipient
     */
    public static AwardRecipient unit(Organization unit) {
        return new AwardRecipient(RecipientType.UNIT, UnitRef.of(unit));
    }
}
