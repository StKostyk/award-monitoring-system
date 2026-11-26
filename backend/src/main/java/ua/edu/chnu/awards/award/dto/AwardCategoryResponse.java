package ua.edu.chnu.awards.award.dto;

import java.util.List;

import ua.edu.chnu.awards.award.entity.RecognitionLevel;

/**
 * A category of the catalogue with its active subcategories.
 *
 * @param id          identifier
 * @param name        English name
 * @param nameUk      Ukrainian name
 * @param description what the category covers
 * @param level       recognition level
 * @param children    subcategories in display order
 */
public record AwardCategoryResponse(Long id, String name, String nameUk, String description,
                                    RecognitionLevel level, List<AwardCategoryResponse> children) {
}
