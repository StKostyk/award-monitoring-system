package ua.edu.chnu.awards.analytics.dto;

/**
 * Counted awards of one calendar month.
 *
 * @param year  calendar year
 * @param month month, 1 to 12
 * @param count number of counted awards; 0 for a month still to come
 */
public record MonthCount(int year, int month, long count) {
}
