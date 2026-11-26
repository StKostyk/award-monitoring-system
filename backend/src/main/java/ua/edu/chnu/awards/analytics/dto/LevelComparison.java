package ua.edu.chnu.awards.analytics.dto;

import ua.edu.chnu.awards.award.entity.RecognitionLevel;

/**
 * Counted awards of one recognition level in the period and in the previous period.
 *
 * @param level    the recognition level
 * @param count    awards in the period
 * @param previous awards in the previous period
 */
public record LevelComparison(RecognitionLevel level, long count, long previous) {
}
