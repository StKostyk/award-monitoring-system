package ua.edu.chnu.awards.analytics.dto;

import java.time.LocalDate;

import ua.edu.chnu.awards.award.entity.RecognitionLevel;

/**
 * Totals of the counted awards.
 *
 * @param approved        number of awards
 * @param verified        awards whose documents a reviewer checked
 * @param highestLevel    the highest recognition level, null without awards
 * @param firstAwardDate  earliest award date, null without awards
 * @param latestAwardDate latest award date, null without awards
 */
public record AnalyticsTotals(long approved, long verified, RecognitionLevel highestLevel, LocalDate firstAwardDate,
                              LocalDate latestAwardDate) {
}
