package ua.edu.chnu.awards.award.dto;

import java.time.LocalDate;

import ua.edu.chnu.awards.award.entity.AwardStatus;

/**
 * Filters of the own award list; null means no filter.
 *
 * @param status     award status
 * @param categoryId category
 * @param dateFrom   earliest award date
 * @param dateTo     latest award date
 * @param q          search text
 */
public record AwardQuery(AwardStatus status, Long categoryId, LocalDate dateFrom, LocalDate dateTo, String q) {

    /**
     * Filters without a search text.
     *
     * @param status     award status
     * @param categoryId category
     * @param dateFrom   earliest award date
     * @param dateTo     latest award date
     */
    public AwardQuery(AwardStatus status, Long categoryId, LocalDate dateFrom, LocalDate dateTo) {
        this(status, categoryId, dateFrom, dateTo, null);
    }
}
