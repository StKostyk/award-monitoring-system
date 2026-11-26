package ua.edu.chnu.awards.award.dto;

import java.time.LocalDate;

import ua.edu.chnu.awards.award.entity.AwardStatus;

/**
 * An award of the same owner that looks like the one being entered.
 *
 * @param id        identifier
 * @param title     English title
 * @param titleUk   Ukrainian title
 * @param awardDate when it was granted
 * @param status    status of the award
 */
public record DuplicateMatch(Long id, String title, String titleUk, LocalDate awardDate, AwardStatus status) {
}
