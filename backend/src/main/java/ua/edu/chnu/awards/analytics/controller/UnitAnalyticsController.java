package ua.edu.chnu.awards.analytics.controller;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.analytics.dto.AnalysableUnit;
import ua.edu.chnu.awards.analytics.dto.UnitAnalytics;
import ua.edu.chnu.awards.analytics.service.UnitAnalyticsService;

import lombok.RequiredArgsConstructor;

/**
 * The analytics of the faculties and departments the caller oversees.
 */
@RestController
@RequestMapping("/api/v1/analytics/units")
@RequiredArgsConstructor
public class UnitAnalyticsController {

    private static final String CAN_READ = "@access.require('analytics:read:department', 'analytics:read:faculty')";

    private final UnitAnalyticsService unitAnalytics;

    /**
     * The faculties and departments covered by the caller's own role scopes.
     *
     * @return each faculty followed by its departments
     */
    @GetMapping
    @PreAuthorize(CAN_READ)
    public ResponseEntity<List<AnalysableUnit>> units() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(unitAnalytics.units());
    }

    /**
     * The analytics of one unit over a period, compared with the previous period of the same length.
     *
     * @param id   the unit
     * @param from first calendar year, by default four years before the last
     * @param to   last calendar year, by default the current year
     * @return the analytics
     */
    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<UnitAnalytics> unit(@PathVariable long id, @RequestParam(required = false) Integer from,
                                              @RequestParam(required = false) Integer to) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(unitAnalytics.of(id, from, to));
    }
}
