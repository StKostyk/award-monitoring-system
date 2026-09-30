package ua.edu.chnu.awards.award.dto;

/**
 * One field that differs from the previous version.
 *
 * @param field snapshot field name
 * @param from  value in the previous version, null when empty
 * @param to    value in this version, null when empty
 */
public record FieldChange(String field, Object from, Object to) {
}
