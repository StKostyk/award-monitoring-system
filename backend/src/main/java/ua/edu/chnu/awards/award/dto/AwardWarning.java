package ua.edu.chnu.awards.award.dto;

/**
 * A hint about the award data that does not block saving.
 *
 * @param code  warning code
 * @param field the field it concerns
 */
public record AwardWarning(String code, String field) {
}
