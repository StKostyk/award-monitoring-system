package ua.edu.chnu.awards.award.mapper;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.award.dto.ReturnedReviewItem;
import ua.edu.chnu.awards.award.dto.UserRef;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.ReviewDecision;
import ua.edu.chnu.awards.award.entity.ReviewDecisionType;
import ua.edu.chnu.awards.award.repository.ReviewDecisionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Builds the in-rework items of returned requests, with the latest return of each page loaded in one query.
 */
@Component
@RequiredArgsConstructor
public class ReturnedItemMapper {

    private final ReviewDecisionRepository decisions;
    private final Clock clock;

    /**
     * The items of several returned requests, in their order.
     *
     * @param requests the requests with their awards
     * @return the items
     */
    public List<ReturnedReviewItem> toItems(List<AwardRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }
        Map<Long, ReviewDecision> latest = new HashMap<>();
        decisions.findLatestFirst(requests.stream().map(AwardRequest::getId).toList(), ReviewDecisionType.RETURNED)
            .forEach(decision -> latest.putIfAbsent(decision.getRequestId(), decision));
        return requests.stream().map(request -> item(request, latest.get(request.getId()))).toList();
    }

    private ReturnedReviewItem item(AwardRequest request, ReviewDecision decision) {
        Award award = request.getAward();
        if (decision == null) {
            return new ReturnedReviewItem(award.getId(), request.getId(), award.getTitle(), award.getTitleUk(),
                AwardMapper.recipient(award), UserRef.of(award.getOwner()),
                AwardMapper.organizationRef(award.getOrganization()), AwardMapper.categoryRef(award.getCategory()),
                request.getCurrentLevel(), null, null, null, null, 0);
        }
        long days = Duration.between(decision.getDecidedAt(), clock.instant()).toDays();
        return new ReturnedReviewItem(award.getId(), request.getId(), award.getTitle(), award.getTitleUk(),
            AwardMapper.recipient(award), UserRef.of(award.getOwner()),
            AwardMapper.organizationRef(award.getOrganization()), AwardMapper.categoryRef(award.getCategory()),
            decision.getLevel(), decision.getDecidedAt(), UserRef.of(decision.getReviewer()),
            UserRef.of(decision.getDelegator()), decision.getComments(), Math.max(0, days));
    }
}
