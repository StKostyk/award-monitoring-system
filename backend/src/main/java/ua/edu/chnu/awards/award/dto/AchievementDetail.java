package ua.edu.chnu.awards.award.dto;

import java.time.Instant;
import java.util.List;

/**
 * A shared award as a colleague sees it: the achievement, the documents its owner shows to colleagues and when its
 * review ended.
 *
 * @param achievement the achievement card
 * @param documents   the documents shown to colleagues, oldest first; empty for a unit award
 * @param approvedAt  when the last reviewer approved it; null for an award approved without a review
 */
public record AchievementDetail(Achievement achievement, List<SharedDocument> documents, Instant approvedAt) {

    /**
     * Keeps an unmodifiable copy of the documents.
     *
     * @param achievement the achievement card
     * @param documents   the documents shown to colleagues
     * @param approvedAt  when the last reviewer approved it
     */
    public AchievementDetail {
        documents = List.copyOf(documents);
    }
}
