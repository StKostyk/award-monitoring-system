package ua.edu.chnu.awards.award.dto;

/**
 * Who received a shared award.
 *
 * @param type       a person or a unit
 * @param personName first and last name of the owner of a personal award, null for a unit award
 * @param unit       the recipient unit, or the person's department at submission
 */
public record AchievementRecipient(RecipientType type, String personName, UnitRef unit) {
}
