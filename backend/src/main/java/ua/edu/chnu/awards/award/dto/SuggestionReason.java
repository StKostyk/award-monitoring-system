package ua.edu.chnu.awards.award.dto;

/**
 * Why a category is suggested for an award.
 */
public enum SuggestionReason {
    /** Keywords of the category or of its level appear in the title or the awarding organisation. */
    KEYWORD,
    /** The awarding organisation names a unit of the university of the category's level. */
    ORGANISATION,
    /** The caller often chose the category before. */
    HISTORY
}
