package ua.edu.chnu.awards.award.service;

import java.time.Clock;
import java.util.Arrays;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.event.ReviewTakenOver;
import ua.edu.chnu.awards.user.entity.User;

import lombok.RequiredArgsConstructor;

/**
 * When the caller may take a request over from its holder: when the caller's level is above the holder's, when the
 * holder no longer holds an approving role or delegation at the request's level, or, for a peer at the holder's
 * level, once the request is past its deadline. The previous holder is told about a take-over.
 */
@Component
@RequiredArgsConstructor
public class TakeOverRule {

    private final ReviewerRule rule;
    private final ReviewerAvailability availability;
    private final AccessScope access;
    private final StatusEstimator estimator;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * Whether the caller may take the request over from a colleague who holds it.
     *
     * @param request the request
     * @return false when nobody or the caller holds it
     */
    public boolean offered(AwardRequest request) {
        User holder = request.getCurrentReviewer();
        return holder != null && holder.getId() != access.callerId() && allows(request, holder);
    }

    /**
     * Whether the caller may take the request over from its holder.
     *
     * @param request the request
     * @param holder  who holds it
     * @return true when the caller outranks the holder, the holder may no longer review it, or the caller is the
     *         holder's peer and the request is overdue
     */
    public boolean allows(AwardRequest request, User holder) {
        return !holderReaches(request, holder, request.getCurrentLevel())
            || rule.highestLevel(request).filter(level -> !holderReaches(request, holder, level)
                || isOverdue(request) && !holderOutranks(request, holder, level)).isPresent();
    }

    /**
     * Tells the previous holder, once the surrounding transaction commits, who took the request over.
     *
     * @param request the request taken over
     * @param holder  who held it
     * @param caller  who holds it now
     */
    public void announce(AwardRequest request, User holder, User caller) {
        Award award = request.getAward();
        events.publishEvent(new ReviewTakenOver(holder.getId(), holder.getEmailAddress(), holder.getFirstName(),
            award.getId(), award.titleInEnglish(), award.titleInUkrainian(), caller.getFullName(), clock.instant()));
    }

    private boolean isOverdue(AwardRequest request) {
        return estimator.timeline(request.getAward(), request).overdue();
    }

    private boolean holderOutranks(AwardRequest request, User holder, ApprovalLevel callerLevel) {
        return Arrays.stream(ApprovalLevel.values())
            .filter(level -> !callerLevel.covers(level))
            .anyMatch(level -> holderReaches(request, holder, level));
    }

    private boolean holderReaches(AwardRequest request, User holder, ApprovalLevel level) {
        return availability.isEligible(holder.getId(), level, request.getAward().getOrganization().getId(),
            request.getAward().getOwner().getId(), request.getSubmitter().getId());
    }
}
