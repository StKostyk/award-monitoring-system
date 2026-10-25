package ua.edu.chnu.awards.analytics.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.analytics.dto.ComplianceSummary;
import ua.edu.chnu.awards.analytics.dto.UniversityAnalytics;
import ua.edu.chnu.awards.analytics.service.UniversityAnalyticsService;
import ua.edu.chnu.awards.analytics.service.UniversityComplianceService;

import lombok.RequiredArgsConstructor;

/**
 * The university-wide analytics and the compliance summary of the executive dashboard.
 */
@RestController
@RequestMapping("/api/v1/analytics/university")
@RequiredArgsConstructor
public class UniversityAnalyticsController {

    private static final String CAN_READ = "@access.require('analytics:read:university')";

    private final UniversityAnalyticsService universityAnalytics;
    private final UniversityComplianceService compliance;

    /**
     * The analytics of the whole university over a period, compared with the previous period of the same length.
     *
     * @param from first calendar year, by default four years before the last
     * @param to   last calendar year, by default the current year
     * @return the analytics
     */
    @GetMapping
    @PreAuthorize(CAN_READ)
    public ResponseEntity<UniversityAnalytics> university(@RequestParam(required = false) Integer from,
                                                          @RequestParam(required = false) Integer to) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(universityAnalytics.of(from, to));
    }

    /**
     * The state of the data protection and review duties, read at the request.
     *
     * @return counts only, with the reasons for attention
     */
    @GetMapping("/compliance")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ComplianceSummary> compliance() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(compliance.summary());
    }
}
