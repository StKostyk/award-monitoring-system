package ua.edu.chnu.awards.analytics.dto;

import ua.edu.chnu.awards.organization.dto.OrganizationRef;

/**
 * The person's place among the active accounts of the department.
 *
 * @param status     whether the figures are shown
 * @param position   competition rank, null unless ranked
 * @param groupSize  active accounts of the department, null unless ranked
 * @param sharedWith other members with the same count, null unless ranked
 * @param department the department, null without consent or department
 */
public record PersonalRank(RankStatus status, Integer position, Integer groupSize, Integer sharedWith,
                           OrganizationRef department) {

    /**
     * A rank without figures or department.
     *
     * @param status why there is no rank
     * @return the rank
     */
    public static PersonalRank without(RankStatus status) {
        return new PersonalRank(status, null, null, null, null);
    }
}
