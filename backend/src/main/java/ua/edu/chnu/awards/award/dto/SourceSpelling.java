package ua.edu.chnu.awards.award.dto;

/**
 * Approved awards with one spelling of an awarding organisation.
 *
 * @param key      the spelling in lower case with spaces collapsed, shared by every spelling of the same name
 * @param spelling the text as entered
 * @param count    number of awards
 */
public record SourceSpelling(String key, String spelling, long count) {
}
