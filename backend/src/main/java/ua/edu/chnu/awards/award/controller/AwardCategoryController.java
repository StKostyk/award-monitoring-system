package ua.edu.chnu.awards.award.controller;

import java.time.Duration;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.award.dto.AwardCategoryResponse;
import ua.edu.chnu.awards.award.dto.CategorySuggestion;
import ua.edu.chnu.awards.award.service.CategoryCatalogue;
import ua.edu.chnu.awards.award.service.CategorySuggester;

import lombok.RequiredArgsConstructor;

/**
 * Award category catalogue for every signed-in user, and category suggestions for those who enter awards.
 */
@RestController
@RequestMapping("/api/v1/award-categories")
@RequiredArgsConstructor
public class AwardCategoryController {

    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofHours(1));

    private final CategoryCatalogue catalogue;
    private final CategorySuggester suggester;

    /**
     * Active categories as a tree with their entity tag; a matching {@code If-None-Match} is answered with 304
     * by Spring MVC.
     *
     * @return the tree
     */
    @GetMapping
    public ResponseEntity<List<AwardCategoryResponse>> tree() {
        List<AwardCategoryResponse> roots = catalogue.tree();
        return ResponseEntity.ok().cacheControl(CACHE).eTag(catalogue.etag(roots)).body(roots);
    }

    /**
     * Up to three categories for the award being entered, ranked, each with the reasons that produced it.
     *
     * @param title        the award title in either language
     * @param organization the awarding organisation
     * @return the suggestions; empty when both inputs are shorter than three characters
     */
    @GetMapping("/suggestions")
    @PreAuthorize("@access.require('award:create')")
    public List<CategorySuggestion> suggestions(@RequestParam(required = false) String title,
                                                @RequestParam(required = false) String organization) {
        return suggester.suggest(title, organization);
    }
}
