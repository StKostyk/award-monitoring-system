package ua.edu.chnu.awards.award.service;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.user.entity.User;

import lombok.RequiredArgsConstructor;

/**
 * When the caller may take a request over from its holder: when the caller's level is above the holder's, or when
 * the holder no longer holds an approving role or delegation at the request's level.
 */
@Component
@RequiredArgsConstructor
public class TakeOverRule {

    private final ReviewerRule rule;
    private final ReviewerAvailability availability;
    private final AccessScope access;

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
     * @return true when the caller outranks the holder or the holder may no longer review it
     */
    public boolean allows(AwardRequest request, User holder) {
        return !holderReaches(request, holder, request.getCurrentLevel())
            || rule.highestLevel(request).filter(level -> !holderReaches(request, holder, level)).isPresent();
    }

    private boolean holderReaches(AwardRequest request, User holder, ApprovalLevel level) {
        return availability.isEligible(holder.getId(), level, request.getAward().getOrganization().getId(),
            request.getAward().getOwner().getId(), request.getSubmitter().getId());
    }
}
