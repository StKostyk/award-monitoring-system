package ua.edu.chnu.awards.common.web;

/**
 * One refused field of a form, as listed in the {@code errors} member of a problem body.
 *
 * @param field   field name as the request spells it
 * @param code    what is wrong, for the client to phrase: {@code required}, {@code too-long}, {@code invalid},
 *                {@code inactive}, {@code future}
 * @param message English explanation
 */
public record FieldViolation(String field, String code, String message) {
}
