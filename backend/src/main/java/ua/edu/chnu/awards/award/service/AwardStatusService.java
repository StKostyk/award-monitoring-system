package ua.edu.chnu.awards.award.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.award.dto.AwardStatusView;
import ua.edu.chnu.awards.award.dto.DecisionView;
import ua.edu.chnu.awards.award.dto.DelayReason;
import ua.edu.chnu.awards.award.dto.PathStep;
import ua.edu.chnu.awards.award.dto.StatusDelay;
import ua.edu.chnu.awards.award.dto.StepState;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.entity.ReviewDecision;
import ua.edu.chnu.awards.award.entity.ReviewDecisionType;
import ua.edu.chnu.awards.award.repository.AwardRequestRepository;
import ua.edu.chnu.awards.award.repository.ReviewDecisionRepository;

import lombok.RequiredArgsConstructor;

/**
 * The review timeline of an award for whoever may read the award: the levels of its approval path, the
 * expected completion, the reason of a delay and the reviewer decisions with their comments.
 */
@Service
@RequiredArgsConstructor
public class AwardStatusService {

    private static final Set<ReviewDecisionType> PASSED =
        EnumSet.of(ReviewDecisionType.APPROVED, ReviewDecisionType.ESCALATED);

    private final AwardRequestRepository requests;
    private final ReviewDecisionRepository decisions;
    private final AwardOwnership ownership;
    private final StatusEstimator estimator;
    private final ReviewerAvailability reviewers;

    /**
     * The timeline of an award the caller may read.
     *
     * @param id the award
     * @return the timeline; without a request for a draft
     * @throws AwardNotFoundException when it does not exist or is not readable by the caller
     */
    @Transactional(readOnly = true)
    public AwardStatusView status(long id) {
        Award award = ownership.readable(id);
        AwardRequest request = award.isDraft() ? null : requests.findByAwardId(id).orElse(null);
        if (request == null) {
            return AwardStatusView.withoutRequest(award.getId(), award.getStatus());
        }
        StatusEstimator.Timeline timeline = estimator.timeline(award, request);
        List<ReviewDecision> made = decisions.findByRequestId(request.getId());
        return new AwardStatusView(award.getId(), award.getStatus(), request.getStatus(), request.getCurrentLevel(),
            request.getSubmittedAt(), timeline.deadline(), timeline.estimatedCompletion(), timeline.overdue(),
            request.getCompletedAt(), request.getRejectionReason(), delay(award, request, timeline),
            steps(request, timeline, made), made.stream().map(DecisionView::of).toList());
    }

    private StatusDelay delay(Award award, AwardRequest request, StatusEstimator.Timeline timeline) {
        if (!estimator.isActive(request)) {
            return null;
        }
        if (!reviewers.hasReviewer(request.getCurrentLevel(), award.getOrganization().getId(),
            award.getOwner().getId())) {
            return new StatusDelay(DelayReason.NO_REVIEWER, null);
        }
        return timeline.overdue() ? new StatusDelay(DelayReason.REVIEW_OVERDUE, timeline.deadline()) : null;
    }

    private static List<PathStep> steps(AwardRequest request, StatusEstimator.Timeline timeline,
                                        List<ReviewDecision> made) {
        int current = timeline.levels().indexOf(request.getCurrentLevel());
        List<PathStep> steps = new ArrayList<>();
        for (int i = 0; i < timeline.levels().size(); i++) {
            ApprovalLevel level = timeline.levels().get(i);
            boolean done = i < current || i == current && request.getStatus() == RequestStatus.APPROVED;
            StepState state = done ? StepState.DONE : i == current ? StepState.CURRENT : StepState.UPCOMING;
            steps.add(new PathStep(level, state, done ? null : timeline.due().get(level),
                done ? passedAt(made, level) : null));
        }
        return steps;
    }

    private static Instant passedAt(List<ReviewDecision> made, ApprovalLevel level) {
        return made.stream()
            .filter(decision -> decision.getLevel() == level && PASSED.contains(decision.getDecision()))
            .map(ReviewDecision::getDecidedAt)
            .reduce((first, second) -> second)
            .orElse(null);
    }
}
