package ua.edu.chnu.awards.award.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.award.dto.AwardCategoryResponse;
import ua.edu.chnu.awards.award.entity.AwardCategory;
import ua.edu.chnu.awards.award.repository.AwardCategoryRepository;
import ua.edu.chnu.awards.common.HashUtils;

import lombok.RequiredArgsConstructor;

/**
 * The award category catalogue as a tree of active categories.
 */
@Service
@RequiredArgsConstructor
public class CategoryCatalogue {

    private final AwardCategoryRepository repository;

    /**
     * Strong entity tag of a tree, changing whenever its content does.
     *
     * @param roots the tree
     * @return quoted entity tag
     */
    public String etag(List<AwardCategoryResponse> roots) {
        return "\"" + HashUtils.sha256Hex(roots.toString()) + "\"";
    }

    /**
     * Active categories nested under their parents; a category whose parent is inactive is left out.
     *
     * @return top-level categories in display order
     */
    @Transactional(readOnly = true)
    public List<AwardCategoryResponse> tree() {
        List<AwardCategory> active = repository.findByActiveTrueOrderBySortOrderAscNameAsc();
        Map<Long, List<AwardCategory>> children = new LinkedHashMap<>();
        List<AwardCategory> roots = new ArrayList<>();
        for (AwardCategory category : active) {
            if (category.getParent() == null) {
                roots.add(category);
            } else {
                children.computeIfAbsent(category.getParent().getId(), id -> new ArrayList<>()).add(category);
            }
        }
        return roots.stream().map(root -> node(root, children)).toList();
    }

    private static AwardCategoryResponse node(AwardCategory category, Map<Long, List<AwardCategory>> children) {
        List<AwardCategoryResponse> nested = children.getOrDefault(category.getId(), List.of()).stream()
            .map(child -> node(child, children))
            .toList();
        return new AwardCategoryResponse(category.getId(), category.getName(), category.getNameUk(),
            category.getDescription(), category.getLevel(), nested);
    }
}
