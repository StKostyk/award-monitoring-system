package ua.edu.chnu.awards.analytics.dto;

import ua.edu.chnu.awards.award.dto.CategoryRef;

/**
 * Counted awards of one category in the period and in the previous period.
 *
 * @param category the category with its level
 * @param count    awards in the period
 * @param previous awards in the previous period
 */
public record CategoryComparison(CategoryRef category, long count, long previous) {
}
