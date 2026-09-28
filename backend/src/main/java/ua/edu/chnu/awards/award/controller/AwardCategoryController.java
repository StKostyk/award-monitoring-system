package ua.edu.chnu.awards.award.controller;

import java.time.Duration;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.award.dto.AwardCategoryResponse;
import ua.edu.chnu.awards.award.service.CategoryCatalogue;

import lombok.RequiredArgsConstructor;

/**
 * Award category catalogue for every signed-in user.
 */
@RestController
@RequestMapping("/api/v1/award-categories")
@RequiredArgsConstructor
public class AwardCategoryController {

    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofHours(1));

    private final CategoryCatalogue catalogue;

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
}
