package ua.edu.chnu.awards.award.dto;

import java.util.List;

import ua.edu.chnu.awards.award.entity.RecognitionLevel;

/**
 * A category suggested for the award being entered.
 *
 * @param id      category identifier
 * @param name    English name
 * @param nameUk  Ukrainian name
 * @param level   recognition level
 * @param score   rank of the suggestion, higher first
 * @param reasons the rules that produced it
 */
public record CategorySuggestion(Long id, String name, String nameUk, RecognitionLevel level, int score,
                                 List<SuggestionReason> reasons) {

    /**
     * Keeps an unmodifiable copy of the reasons.
     */
    public CategorySuggestion {
        reasons = List.copyOf(reasons);
    }
}
