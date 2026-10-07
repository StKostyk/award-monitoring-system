package ua.edu.chnu.awards.award.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.award.dto.ReviewDecisionRequest.Decision;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.ReviewDecision;
import ua.edu.chnu.awards.award.entity.ReviewDecisionType;
import ua.edu.chnu.awards.award.event.AwardDecided;
import ua.edu.chnu.awards.award.repository.ReviewDecisionRepository;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Writes the trace of a reviewer decision: the decision row of the deciding level, the audit entry and the event
 * that tells the owner after the commit.
 */
@Component
@RequiredArgsConstructor
public class DecisionLog {

    private final ReviewDecisionRepository decisions;
    private final UserRepository users;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    /**
     * Saves the decision row, inside the caller's transaction.
     *
     * @param request     the request decided on
     * @param level       the deciding level
     * @param type        what the row records
     * @param reviewer    who decided
     * @param comment     the reviewer's comment, null when none
     * @param delegatorId who lent the role the reviewer decided under, null for an own role
     * @param decidedAt   when
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void write(AwardRequest request, ApprovalLevel level, ReviewDecisionType type, User reviewer,
                      String comment, Long delegatorId, Instant decidedAt) {
        decisions.save(ReviewDecision.builder()
            .requestId(request.getId())
            .reviewer(reviewer)
            .decision(type)
            .level(level)
            .comments(comment)
            .delegator(delegatorId == null ? null : users.getReferenceById(delegatorId))
            .decidedAt(decidedAt)
            .build());
    }

    /**
     * Records the decision in the audit log with the request's new state.
     *
     * @param request     the request after the decision
     * @param decision    what the reviewer decided
     * @param level       the deciding level
     * @param reviewerId  who decided
     * @param delegatorId who lent the role, null for an own role
     */
    public void audit(AwardRequest request, Decision decision, ApprovalLevel level, long reviewerId,
                      Long delegatorId) {
        Map<String, Object> details = new HashMap<>();
        details.put("requestId", request.getId());
        details.put("level", level.name());
        details.put("decision", decision.name());
        details.put("requestStatus", request.getStatus().name());
        details.put("newLevel", request.getCurrentLevel().name());
        if (delegatorId != null) {
            details.put("delegatorId", delegatorId);
        }
        audit.record(AuditAction.REVIEW_DECISION, AuditEntityConstants.AWARDS, reviewerId,
            request.getAward().getId(), details);
    }

    /**
     * Publishes the decision for the owner's e-mail, sent once the transaction commits.
     *
     * @param request  the request after the decision
     * @param reviewer who decided
     * @param comment  the reviewer's comment, null when none
     */
    public void announce(AwardRequest request, User reviewer, String comment) {
        Award award = request.getAward();
        User owner = award.getOwner();
        String title = award.getTitle() == null ? award.getTitleUk() : award.getTitle();
        String titleUk = award.getTitleUk() == null ? award.getTitle() : award.getTitleUk();
        events.publishEvent(new AwardDecided(owner.getEmailAddress(), owner.getFirstName(), award.getId(), title,
            titleUk, request.getStatus(), request.getCurrentLevel(), reviewer.getFullName(), comment));
    }
}
