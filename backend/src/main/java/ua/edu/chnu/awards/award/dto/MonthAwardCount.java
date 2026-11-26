package ua.edu.chnu.awards.award.dto;

/**
 * Approved awards submitted in a set of organisations in one calendar month, personal and unit awards alike.
 *
 * @param year  calendar year of the award date
 * @param month month of the award date, 1 to 12
 * @param count number of awards
 */
public record MonthAwardCount(int year, int month, long count) {
}
