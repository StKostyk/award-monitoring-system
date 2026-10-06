package ua.edu.chnu.awards.award.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.authz.OrganizationTree;
import ua.edu.chnu.awards.award.dto.ReviewItem;
import ua.edu.chnu.awards.award.dto.ReviewQuery;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.mapper.ReviewItemMapper;
import ua.edu.chnu.awards.award.repository.AwardRequestRepository;
import ua.edu.chnu.awards.award.repository.ReviewSpecifications;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.PageResponse;

import lombok.RequiredArgsConstructor;

/**
 * The reviewer queue: open requests the caller may review, by default at the levels of the caller's roles,
 * without awards the caller owns or submitted, earliest deadline (so the overdue ones) first.
 */
@Service
@RequiredArgsConstructor
public class ReviewQueue {

    private final AwardRequestRepository requests;
    private final ReviewSpecifications specifications;
    private final ReviewerRule rule;
    private final ReviewItemMapper mapper;
    private final AccessScope access;
    private final OrganizationTree tree;
    private final Clock clock;

    /**
     * A page of the queue.
     *
     * @param query the filters
     * @param page  0-based page
     * @param size  page size, capped at 100
     * @return the page, empty for a caller with nothing to review
     * @throws ApiProblemException 400 {@code invalid-parameter} for a level above the caller's roles or an
     *                             organisation outside their scopes
     */
    @Transactional(readOnly = true)
    public Page<ReviewItem> list(ReviewQuery query, int page, int size) {
        List<ReviewGrant> grants = rule.callerGrants();
        if (query.level() != null && grants.stream().noneMatch(grant -> grant.level().covers(query.level()))) {
            throw invalid("level", "You do not review at level " + query.level());
        }
        if (query.organizationId() != null
            && grants.stream().noneMatch(grant -> tree.covers(grant.organizationId(), query.organizationId()))) {
            throw invalid("organizationId", "Organisation " + query.organizationId() + " is outside your scope");
        }
        PageRequest pageable = PageResponse.request(page, size,
            Sort.by(Sort.Order.asc("deadline"), Sort.Order.asc("id")));
        if (grants.isEmpty()) {
            return Page.empty(pageable);
        }
        Page<AwardRequest> found = requests.findAll(specification(grants, query), pageable);
        return new PageImpl<>(mapper.toItems(found.getContent()), pageable, found.getTotalElements());
    }

    private Specification<AwardRequest> specification(List<ReviewGrant> grants, ReviewQuery query) {
        List<ReviewSpecifications.Reach> reaches = grants.stream()
            .filter(grant -> query.level() == null || grant.level().covers(query.level()))
            .map(grant -> new ReviewSpecifications.Reach(query.level() == null ? grant.level() : query.level(),
                tree.subtree(grant.organizationId()), grant.delegatorId()))
            .toList();
        return specifications.queue(access.callerId(), reaches, query,
            query.organizationId() == null ? null : tree.subtree(query.organizationId()), clock.instant());
    }

    private static ApiProblemException invalid(String parameter, String detail) {
        return new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-parameter", detail,
            Map.of("parameter", parameter));
    }
}
