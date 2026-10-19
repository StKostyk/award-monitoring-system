package ua.edu.chnu.awards.award.dto;

import ua.edu.chnu.awards.award.entity.RecognitionLevel;

/**
 * Filters of the achievements page.
 *
 * @param unit      a faculty with its departments, or a department
 * @param year      calendar year of the award date
 * @param level     recognition level of the category
 * @param recipient personal or unit awards only
 * @param person    the owner of the personal awards; unit awards are left out
 */
public record AchievementQuery(Long unit, Integer year, RecognitionLevel level, RecipientType recipient,
                               Long person) {

    /**
     * Filters without a person.
     *
     * @param unit      a faculty with its departments, or a department
     * @param year      calendar year of the award date
     * @param level     recognition level of the category
     * @param recipient personal or unit awards only
     */
    public AchievementQuery(Long unit, Integer year, RecognitionLevel level, RecipientType recipient) {
        this(unit, year, level, recipient, null);
    }
}
