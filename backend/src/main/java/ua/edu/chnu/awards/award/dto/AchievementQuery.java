package ua.edu.chnu.awards.award.dto;

import ua.edu.chnu.awards.award.entity.RecognitionLevel;

/**
 * Filters of the achievements page.
 *
 * @param unit      a faculty with its departments, or a department
 * @param year      calendar year of the award date
 * @param level     recognition level of the category
 * @param recipient personal or unit awards only
 */
public record AchievementQuery(Long unit, Integer year, RecognitionLevel level, RecipientType recipient) {
}
