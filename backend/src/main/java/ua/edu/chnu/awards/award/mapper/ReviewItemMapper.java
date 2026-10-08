package ua.edu.chnu.awards.award.mapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.award.dto.ReviewItem;
import ua.edu.chnu.awards.award.dto.UserRef;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.service.ReviewGrant;
import ua.edu.chnu.awards.award.service.ReviewerRule;
import ua.edu.chnu.awards.award.service.StatusEstimator;
import ua.edu.chnu.awards.document.repository.DocumentCount;
import ua.edu.chnu.awards.document.repository.DocumentRepository;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Builds the queue items of requests, with document counts and delegators loaded once per page.
 */
@Component
@RequiredArgsConstructor
public class ReviewItemMapper {

    private final StatusEstimator estimator;
    private final ReviewerRule rule;
    private final DocumentRepository documents;
    private final UserRepository users;

    /**
     * The item of one request.
     *
     * @param request the request with its award
     * @return the item
     */
    public ReviewItem toItem(AwardRequest request) {
        return toItems(List.of(request)).getFirst();
    }

    /**
     * The items of several requests, in their order.
     *
     * @param requests the requests with their awards
     * @return the items
     */
    public List<ReviewItem> toItems(List<AwardRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }
        Set<Long> awardIds = requests.stream().map(request -> request.getAward().getId()).collect(Collectors.toSet());
        Map<Long, Long> counts = documents.countByAwardIds(awardIds).stream()
            .collect(Collectors.toMap(DocumentCount::awardId, DocumentCount::count));
        Map<Long, Long> delegatorOf = requests.stream()
            .map(request -> Map.entry(request.getId(), rule.grant(request).map(ReviewGrant::delegatorId)))
            .filter(entry -> entry.getValue().isPresent())
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get()));
        Map<Long, User> delegators = delegatorOf.isEmpty() ? Map.of()
            : users.findAllById(Set.copyOf(delegatorOf.values())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return requests.stream()
            .map(request -> item(request, counts.getOrDefault(request.getAward().getId(), 0L),
                Optional.ofNullable(delegatorOf.get(request.getId())).map(delegators::get).orElse(null)))
            .toList();
    }

    private ReviewItem item(AwardRequest request, long documentCount, User delegator) {
        Award award = request.getAward();
        StatusEstimator.Timeline timeline = estimator.timeline(award, request);
        return new ReviewItem(award.getId(), request.getId(), request.getVersion(), award.getTitle(),
            award.getTitleUk(), AwardMapper.recipient(award), UserRef.of(award.getOwner()),
            AwardMapper.organizationRef(award.getOrganization()), AwardMapper.categoryRef(award.getCategory()),
            request.getCurrentLevel(), request.getStatus(), UserRef.of(request.getCurrentReviewer()),
            request.getSubmittedAt(), timeline.deadline(), timeline.overdue(), request.getOverdueNoticedAt(),
            documentCount,
            UserRef.of(delegator));
    }
}
