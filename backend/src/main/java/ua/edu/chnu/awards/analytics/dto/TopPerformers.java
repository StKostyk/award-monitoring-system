package ua.edu.chnu.awards.analytics.dto;

import java.util.List;

import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.organization.dto.OrganizationRef;

/**
 * The people of a unit with the most counted personal awards who agreed to be named, without positions.
 *
 * @param status whether people are listed
 * @param people by count, then by surname; empty unless listed
 */
public record TopPerformers(Status status, List<Person> people) {

    /**
     * Copies the people.
     *
     * @param status whether people are listed
     * @param people by count, then by surname
     */
    public TopPerformers {
        people = List.copyOf(people);
    }

    /**
     * Whether people can be listed.
     */
    public enum Status {
        /** The unit has enough members; the people with the consent are listed. */
        LISTED,
        /** The unit has fewer active accounts than the minimum group. */
        GROUP_TOO_SMALL
    }

    /**
     * One listed person.
     *
     * @param userId       the account
     * @param firstName    first name
     * @param lastName     surname
     * @param department   the person's current organisation, null without one
     * @param count        counted personal awards in the period
     * @param highestLevel the highest recognition level among them
     */
    public record Person(long userId, String firstName, String lastName, OrganizationRef department, long count,
                         RecognitionLevel highestLevel) {
    }
}
