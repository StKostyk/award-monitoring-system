package ua.edu.chnu.awards.gdpr.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.common.web.PageResponse;
import ua.edu.chnu.awards.gdpr.dto.ActivityEntry;
import ua.edu.chnu.awards.gdpr.dto.PrivacySummary;
import ua.edu.chnu.awards.gdpr.service.PrivacyCentreService;

import lombok.RequiredArgsConstructor;

/**
 * The caller's privacy summary and activity log.
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class PrivacyCentreController {

    private final PrivacyCentreService privacyCentre;

    /**
     * What the caller shares and when the data was last exported.
     *
     * @param jwt the access token
     * @return the summary
     */
    @GetMapping("/privacy")
    public PrivacySummary summary(@AuthenticationPrincipal Jwt jwt) {
        return privacyCentre.summary(Long.parseLong(jwt.getSubject()));
    }

    /**
     * Audit events written by or about the caller, newest first.
     *
     * @param jwt  the access token
     * @param page 0-based page
     * @param size page size, kept between 1 and 100
     * @return the page
     */
    @GetMapping("/activity")
    public PageResponse<ActivityEntry> activity(@AuthenticationPrincipal Jwt jwt,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return privacyCentre.activity(Long.parseLong(jwt.getSubject()), page, size);
    }
}
