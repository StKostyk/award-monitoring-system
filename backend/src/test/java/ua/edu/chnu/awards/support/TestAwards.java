package ua.edu.chnu.awards.support;

import java.time.Instant;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.entity.ReviewDecision;
import ua.edu.chnu.awards.award.entity.ReviewDecisionType;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.User;

/**
 * Award, request and decision entities for unit tests, with defaults that callers override through the builder.
 */
public final class TestAwards {

    public static final long AWARD_ID = 5L;
    public static final long REQUEST_ID = 40L;
    public static final long VERSION = 4L;
    public static final Instant SUBMITTED_AT = Instant.parse("2026-09-28T09:00:00Z");

    private TestAwards() {
    }

    /**
     * A draft titled "Letter" with id {@link #AWARD_ID} and version {@link #VERSION}.
     *
     * @param owner        the owner
     * @param organization the organisation of the award
     * @return the builder
     */
    public static Award.AwardBuilder award(User owner, Organization organization) {
        return Award.builder().id(AWARD_ID).owner(owner).organization(organization).title("Letter")
            .status(AwardStatus.DRAFT).version(VERSION);
    }

    /**
     * A request with id {@link #REQUEST_ID}, submitted by the award's owner at {@link #SUBMITTED_AT} and waiting
     * for the faculty secretary.
     *
     * @param award the award
     * @return the builder
     */
    public static AwardRequest.AwardRequestBuilder request(Award award) {
        return AwardRequest.builder().id(REQUEST_ID).award(award).submitter(award.getOwner())
            .status(RequestStatus.SUBMITTED).currentLevel(ApprovalLevel.FACULTY_SECRETARY).submittedAt(SUBMITTED_AT);
    }

    /**
     * A decision on request {@link #REQUEST_ID} with the comment "comment" plus its id.
     *
     * @param id        the decision id
     * @param type      the decision
     * @param level     the level it was taken at
     * @param reviewer  the reviewer
     * @param decidedAt when it was taken
     * @return the decision
     */
    public static ReviewDecision decision(long id, ReviewDecisionType type, ApprovalLevel level, User reviewer,
                                          Instant decidedAt) {
        return ReviewDecision.builder().id(id).requestId(REQUEST_ID).decision(type).level(level).reviewer(reviewer)
            .comments("comment " + id).decidedAt(decidedAt).build();
    }
}
