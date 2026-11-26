package ua.edu.chnu.awards.analytics.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.analytics.dto.PersonalAnalytics;
import ua.edu.chnu.awards.analytics.service.PersonalAnalyticsService;

import lombok.RequiredArgsConstructor;

/**
 * The caller's own analytics.
 */
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final PersonalAnalyticsService personalAnalytics;

    /**
     * The caller's approved personal awards in a period and the department rank; never cached, so a withdrawn
     * consent takes effect at once.
     *
     * @param jwt  the access token
     * @param from first calendar year, by default the year of the first approved personal award
     * @param to   last calendar year, by default the current year
     * @return the analytics
     */
    @GetMapping("/me")
    @PreAuthorize("@access.require('analytics:read:own')")
    public ResponseEntity<PersonalAnalytics> mine(@AuthenticationPrincipal Jwt jwt,
                                                  @RequestParam(required = false) Integer from,
                                                  @RequestParam(required = false) Integer to) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(personalAnalytics.of(Long.parseLong(jwt.getSubject()), from, to));
    }
}
