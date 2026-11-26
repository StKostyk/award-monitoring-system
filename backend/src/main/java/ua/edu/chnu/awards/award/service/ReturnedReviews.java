package ua.edu.chnu.awards.award.service;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.dto.ReturnedReviewItem;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.mapper.ReturnedItemMapper;
import ua.edu.chnu.awards.award.repository.AwardRequestRepository;
import ua.edu.chnu.awards.award.repository.ReviewSpecifications;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.PageResponse;
import ua.edu.chnu.awards.organization.OrganizationTree;

import lombok.RequiredArgsConstructor;

/**
 * The in-rework tab of the reviewer queue: requests sent back at the levels of the caller's roles, own and
 * delegated, that their owners have not submitted again, the longest waiting first.
 */
@Service
@RequiredArgsConstructor
public class ReturnedReviews {

    private final AwardRequestRepository requests;
    private final ReviewSpecifications specifications;
    private final ReviewerRule rule;
    private final ReturnedItemMapper mapper;
    private final AccessScope access;
    private final OrganizationTree tree;

    /**
     * A page of the tab.
     *
     * @param organizationId a faculty or department inside the caller's scopes, null for all of them
     * @param page           0-based page
     * @param size           page size, capped at 100
     * @return the page, empty for a caller with nothing to review
     * @throws ApiProblemException 400 {@code invalid-parameter} for an organisation outside the caller's scopes
     */
    @Transactional(readOnly = true)
    public Page<ReturnedReviewItem> list(Long organizationId, int page, int size) {
        List<ReviewGrant> grants = rule.callerGrants();
        if (organizationId != null
            && grants.stream().noneMatch(grant -> tree.covers(grant.organizationId(), organizationId))) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-parameter",
                "Organisation " + organizationId + " is outside your scope", Map.of("parameter", "organizationId"));
        }
        PageRequest pageable = PageResponse.request(page, size,
            Sort.by(Sort.Order.asc("updatedAt"), Sort.Order.asc("id")));
        if (grants.isEmpty()) {
            return Page.empty(pageable);
        }
        List<ReviewSpecifications.Reach> reaches = grants.stream()
            .map(grant -> new ReviewSpecifications.Reach(grant.level(), tree.subtree(grant.organizationId()),
                grant.delegatorId()))
            .toList();
        Page<AwardRequest> found = requests.findAll(specifications.returned(access.callerId(), reaches,
            organizationId == null ? null : tree.subtree(organizationId)), pageable);
        return new PageImpl<>(mapper.toItems(found.getContent()), pageable, found.getTotalElements());
    }
}
