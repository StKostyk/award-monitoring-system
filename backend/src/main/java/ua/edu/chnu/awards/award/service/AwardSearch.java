package ua.edu.chnu.awards.award.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.award.dto.SearchTerms;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.award.repository.AwardSpecifications;
import ua.edu.chnu.awards.common.web.PageResponse;

import lombok.RequiredArgsConstructor;

/**
 * Pages of an award list, searched when a search text is given.
 */
@Component
@RequiredArgsConstructor
public class AwardSearch {

    private final AwardRepository awards;
    private final AwardSpecifications specifications;

    /**
     * A page of the awards of a list: in list order without a search text, in relevance order with one. Similar
     * titles are searched only when no award of the list matches the words.
     *
     * @param list   the list rule and its filters
     * @param query  the search text, may be null
     * @param order  the list order
     * @param byName whether the owner's name of a personal award is searched as well
     * @param page   0-based page
     * @param size   page size, capped at 100
     * @return the page
     */
    @Transactional(readOnly = true)
    public Page<Award> page(Specification<Award> list, String query, Sort order, boolean byName, int page,
                            int size) {
        SearchTerms search = SearchTerms.of(query);
        if (search == null) {
            return awards.findAll(list, PageResponse.request(page, size, order));
        }
        PageRequest unordered = PageResponse.request(page, size, Sort.unsorted());
        Page<Award> found = awards.findAll(list.and(specifications.matching(search, order, byName)), unordered);
        if (found.getTotalElements() > 0 || search.isEmpty()) {
            return found;
        }
        awards.useWordSimilarity(String.valueOf(SearchTerms.MIN_SIMILARITY));
        return awards.findAll(list.and(specifications.similar(search, order)), unordered);
    }
}
