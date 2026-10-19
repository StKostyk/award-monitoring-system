package ua.edu.chnu.awards.analytics.dto;

import ua.edu.chnu.awards.award.entity.RecognitionLevel;

/**
 * Counted awards of one recognition level.
 *
 * @param level the level
 * @param count number of awards
 */
public record LevelCount(RecognitionLevel level, long count) {
}
