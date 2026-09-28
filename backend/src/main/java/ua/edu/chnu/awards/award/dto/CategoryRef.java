package ua.edu.chnu.awards.award.dto;

import ua.edu.chnu.awards.award.entity.RecognitionLevel;

/**
 * The category of an award.
 *
 * @param id     identifier
 * @param name   English name
 * @param nameUk Ukrainian name
 * @param level  recognition level
 */
public record CategoryRef(Long id, String name, String nameUk, RecognitionLevel level) {
}
