package ua.edu.chnu.awards.award.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.repository.AwardRequestRepository;
import ua.edu.chnu.awards.award.repository.ReviewDecisionRepository;
import ua.edu.chnu.awards.common.erasure.ErasureContext;
import ua.edu.chnu.awards.common.erasure.ErasureContributor;
import ua.edu.chnu.awards.common.erasure.ErasureOrderConstants;

import lombok.RequiredArgsConstructor;

/**
 * Erasure in the award workflow: claims the person holds go back to their level's queue, approved awards stay as
 * private records under the pseudonym, every other award goes with its request, decisions and versions, and the
 * person no longer appears as the actor of any award version. Decisions the person made on other people's awards
 * stay.
 */
@Component
@RequiredArgsConstructor
public class AwardErasure implements ErasureContributor {

    private static final String DELETED_AWARDS = """
        select award_id from awards where user_id = ? and status <> 'APPROVED'
        """;

    private static final String DELETED_REQUESTS = """
        select r.request_id from award_requests r
          join awards a on a.award_id = r.award_id
         where a.user_id = ? and a.status <> 'APPROVED'
        """;

    private static final String DELETE_AWARDS = "delete from awards where user_id = ? and status <> 'APPROVED'";

    private static final String HIDE_APPROVED = """
        update awards set visibility = 'PRIVATE', version = version + 1
         where user_id = ? and status = 'APPROVED' and visibility <> 'PRIVATE'
        """;

    private static final String KEPT = "select count(*) from awards where user_id = ? and status = 'APPROVED'";

    private static final String CLEAR_ACTOR = "update award_versions set actor_id = null where actor_id = ?";

    private final AwardRequestRepository requests;
    private final ReviewDecisionRepository decisions;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    @Override
    public int order() {
        return ErasureOrderConstants.AWARDS;
    }

    @Override
    public void erase(ErasureContext context) {
        long userId = context.userId();
        context.count("claimsReleased", releaseClaims(userId));
        List<Long> awards = jdbc.queryForList(DELETED_AWARDS, Long.class, userId);
        context.erased("awards", awards);
        context.erased("award_requests", jdbc.queryForList(DELETED_REQUESTS, Long.class, userId));
        jdbc.update(DELETE_AWARDS, userId);
        context.count("awardsDeleted", awards.size());
        context.count("awardsHidden", jdbc.update(HIDE_APPROVED, userId));
        context.count("awardsKept", Objects.requireNonNull(jdbc.queryForObject(KEPT, Integer.class, userId)));
        context.count("versionActorsCleared", jdbc.update(CLEAR_ACTOR, userId));
    }

    private int releaseClaims(long userId) {
        List<AwardRequest> held = requests.findByCurrentReviewerIdAndStatus(userId, RequestStatus.IN_REVIEW).stream()
            .filter(request -> request.getAward().getOwner().getId() != userId)
            .toList();
        held.forEach(request -> {
            List<ApprovalLevel> below = request.getCurrentLevel().below();
            boolean decidedBelow = !below.isEmpty()
                && decisions.existsByRequestIdAndLevelIn(request.getId(), below);
            request.setCurrentReviewer(null);
            request.setStatus(decidedBelow ? RequestStatus.ESCALATED : RequestStatus.SUBMITTED);
            audit.record(AuditAction.REVIEW_RELEASED, AuditEntityConstants.AWARDS, null, request.getAward().getId(),
                Map.of("requestId", request.getId(), "level", request.getCurrentLevel().name(),
                    "reason", "erasure"));
        });
        requests.saveAllAndFlush(held);
        return held.size();
    }
}
