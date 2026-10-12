package ua.edu.chnu.awards.award.dto;

import java.util.List;

import ua.edu.chnu.awards.document.dto.SharedDocument;

/**
 * A shared award as a colleague sees it: the achievement and the documents its owner shows to colleagues.
 *
 * @param achievement the achievement card
 * @param documents   the documents shown to colleagues, oldest first; empty for a unit award
 */
public record AchievementDetail(Achievement achievement, List<SharedDocument> documents) {

    /**
     * Keeps an unmodifiable copy of the documents.
     *
     * @param achievement the achievement card
     * @param documents   the documents shown to colleagues
     */
    public AchievementDetail {
        documents = List.copyOf(documents);
    }
}
