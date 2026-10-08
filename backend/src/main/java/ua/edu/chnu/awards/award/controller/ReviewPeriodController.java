package ua.edu.chnu.awards.award.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.award.dto.ReviewPeriodResponse;
import ua.edu.chnu.awards.award.dto.ReviewPeriodUpdate;
import ua.edu.chnu.awards.award.service.ReviewPeriods;

import lombok.RequiredArgsConstructor;

/**
 * The review period of a faculty, for reviewers and system administrators.
 */
@RestController
@RequestMapping("/api/v1/organizations/{id}/review-period")
@RequiredArgsConstructor
public class ReviewPeriodController {

    private static final String CAN_READ = "@access.require('award:approve:level1', 'system:configure')";

    private final ReviewPeriods reviewPeriods;

    /**
     * The review period of a faculty.
     *
     * @param id the faculty
     * @return its own, effective and default period
     */
    @GetMapping
    @PreAuthorize(CAN_READ)
    public ReviewPeriodResponse get(@PathVariable long id) {
        return reviewPeriods.read(id);
    }

    /**
     * Sets or resets the review period of a faculty.
     *
     * @param id     the faculty
     * @param update the new period, null for the global default
     * @return the period after the change
     */
    @PutMapping
    @PreAuthorize(CAN_READ)
    public ReviewPeriodResponse update(@PathVariable long id, @RequestBody ReviewPeriodUpdate update) {
        return reviewPeriods.update(id, update);
    }
}
