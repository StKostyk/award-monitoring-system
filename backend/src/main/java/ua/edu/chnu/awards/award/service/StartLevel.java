package ua.edu.chnu.awards.award.service;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;

import lombok.RequiredArgsConstructor;

/**
 * The level a request goes to: a level whose only reviewer would be the submitter is passed over, so nobody
 * reviews an award they entered; a level without any reviewer is kept, and the status page reports the vacancy.
 */
@Component
@RequiredArgsConstructor
public class StartLevel {

    private final ReviewerAvailability reviewers;

    /**
     * The level a new submission starts at.
     *
     * @param organizationId the award's organisation
     * @param submitterId    who submits it
     * @return the faculty secretary, or the first level above that the submitter does not hold alone
     */
    public ApprovalLevel of(long organizationId, long submitterId) {
        return from(ApprovalLevel.FACULTY_SECRETARY, organizationId, submitterId);
    }

    /**
     * The first level from the given one that the submitter does not hold alone; the rector level is kept.
     *
     * @param first          the level to start from
     * @param organizationId the award's organisation
     * @param submitterId    who submitted the award
     * @return the level
     */
    public ApprovalLevel from(ApprovalLevel first, long organizationId, long submitterId) {
        ApprovalLevel[] levels = ApprovalLevel.values();
        int index = first.ordinal();
        while (index < levels.length - 1 && reviewers.isHeldOnlyBy(levels[index], organizationId, submitterId)) {
            index++;
        }
        return levels[index];
    }
}
