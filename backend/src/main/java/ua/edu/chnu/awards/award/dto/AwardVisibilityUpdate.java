package ua.edu.chnu.awards.award.dto;

import ua.edu.chnu.awards.award.entity.AwardVisibility;

/**
 * The owner's choice of who sees an approved personal award.
 *
 * @param visibility the new visibility
 */
public record AwardVisibilityUpdate(AwardVisibility visibility) {
}
