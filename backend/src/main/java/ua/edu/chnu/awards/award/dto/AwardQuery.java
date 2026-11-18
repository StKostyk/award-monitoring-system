package ua.edu.chnu.awards.award.dto;

import java.time.LocalDate;

import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.AwardVisibility;

/**
 * Filters of the own award list; null means no filter.
 *
 * @param status     award status
 * @param categoryId category
 * @param dateFrom   earliest award date
 * @param dateTo     latest award date
 * @param q          search text
 * @param visibility the owner's visibility choice
 */
public record AwardQuery(AwardStatus status, Long categoryId, LocalDate dateFrom, LocalDate dateTo, String q,
                         AwardVisibility visibility) {

    /**
     * Filters without a search text.
     *
     * @param status     award status
     * @param categoryId category
     * @param dateFrom   earliest award date
     * @param dateTo     latest award date
     */
    public AwardQuery(AwardStatus status, Long categoryId, LocalDate dateFrom, LocalDate dateTo) {
        this(status, categoryId, dateFrom, dateTo, null, null);
    }

    /**
     * Filters without a visibility.
     *
     * @param status     award status
     * @param categoryId category
     * @param dateFrom   earliest award date
     * @param dateTo     latest award date
     * @param q          search text
     */
    public AwardQuery(AwardStatus status, Long categoryId, LocalDate dateFrom, LocalDate dateTo, String q) {
        this(status, categoryId, dateFrom, dateTo, q, null);
    }
}
