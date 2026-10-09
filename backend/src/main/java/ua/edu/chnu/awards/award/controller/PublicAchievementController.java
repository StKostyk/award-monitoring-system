package ua.edu.chnu.awards.award.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.award.dto.Achievement;
import ua.edu.chnu.awards.award.dto.AchievementQuery;
import ua.edu.chnu.awards.award.dto.RecipientType;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.award.service.Achievements;
import ua.edu.chnu.awards.common.web.PageResponse;

import lombok.RequiredArgsConstructor;

/**
 * The public achievements page, open to everyone without signing in.
 */
@RestController
@RequestMapping("/api/v1/public/achievements")
@RequiredArgsConstructor
public class PublicAchievementController {

    private final Achievements achievements;

    /**
     * Public personal awards and unit awards, newest award date first; never cached, so a withdrawn choice takes
     * effect at once.
     *
     * @param unit      a faculty with its departments, or a department
     * @param year      calendar year of the award date
     * @param level     recognition level of the category
     * @param recipient personal or unit awards only
     * @param page      0-based page
     * @param size      page size, capped at 100
     * @return the page
     */
    @GetMapping
    public ResponseEntity<PageResponse<Achievement>> list(@RequestParam(required = false) Long unit,
                                                          @RequestParam(required = false) Integer year,
                                                          @RequestParam(required = false) RecognitionLevel level,
                                                          @RequestParam(required = false) RecipientType recipient,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(PageResponse.of(achievements.published(new AchievementQuery(unit, year, level, recipient), page,
                size)));
    }
}
