package ua.edu.chnu.awards.award.dto;

import java.time.LocalDate;

/**
 * Totals of the counted awards of a person in a period.
 *
 * @param approved        number of awards
 * @param verified        awards whose documents a reviewer checked
 * @param firstAwardDate  earliest award date, null without awards
 * @param latestAwardDate latest award date, null without awards
 */
public record CountedTotals(long approved, long verified, LocalDate firstAwardDate, LocalDate latestAwardDate) {
}
