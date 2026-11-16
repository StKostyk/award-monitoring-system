package ua.edu.chnu.awards.award.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * A person with their awards, newest first.
 *
 * @param person the person
 * @param awards the awards
 */
public record PersonDetail(@JsonUnwrapped Person person, List<AwardResponse> awards) {

    /**
     * Keeps an unmodifiable copy of the awards.
     *
     * @param person the person
     * @param awards the awards
     */
    public PersonDetail {
        awards = List.copyOf(awards);
    }
}
