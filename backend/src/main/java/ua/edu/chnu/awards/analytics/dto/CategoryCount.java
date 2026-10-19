package ua.edu.chnu.awards.analytics.dto;

import ua.edu.chnu.awards.award.dto.CategoryRef;

/**
 * Counted awards of one category.
 *
 * @param category the category with its level
 * @param count    number of awards
 */
public record CategoryCount(CategoryRef category, long count) {
}
