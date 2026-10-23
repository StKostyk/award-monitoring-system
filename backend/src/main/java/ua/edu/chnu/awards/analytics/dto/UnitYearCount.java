package ua.edu.chnu.awards.analytics.dto;

/**
 * Counted awards of a unit in one calendar year.
 *
 * @param year       the calendar year
 * @param total      every counted award
 * @param personal   counted personal awards
 * @param unitAwards counted unit awards
 */
public record UnitYearCount(int year, long total, long personal, long unitAwards) {
}
