package ua.edu.chnu.awards.award.dto;

import java.time.LocalDate;

import ua.edu.chnu.awards.award.entity.RecognitionLevel;

/**
 * One counted award on a personal timeline.
 *
 * @param awardId              identifier
 * @param title                English title
 * @param titleUk              Ukrainian title
 * @param awardDate            date of the award
 * @param category             category with its level
 * @param awardingOrganization who gave the award
 * @param verified             whether the reviewer checked the documents
 * @param level                recognition level of the category
 */
public record TimelineEntry(Long awardId, String title, String titleUk, LocalDate awardDate, CategoryRef category,
                            String awardingOrganization, boolean verified, RecognitionLevel level) {
}
