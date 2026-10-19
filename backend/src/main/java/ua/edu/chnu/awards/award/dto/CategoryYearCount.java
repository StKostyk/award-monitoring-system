package ua.edu.chnu.awards.award.dto;

/**
 * Counted awards of one category in one calendar year.
 *
 * @param year     calendar year of the award date
 * @param category the category with its level
 * @param count    number of awards
 */
public record CategoryYearCount(int year, CategoryRef category, long count) {
}
