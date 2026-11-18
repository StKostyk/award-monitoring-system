package ua.edu.chnu.awards.award.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.award.dto.ReviewDay;
import ua.edu.chnu.awards.award.service.ReviewTally;

import lombok.RequiredArgsConstructor;

/**
 * The reviewer's work of the day, for the Command centre.
 */
@RestController
@RequiredArgsConstructor
public class ReviewDayController {

    private final ReviewTally tally;

    /**
     * The decisions the caller made today, from 00:00 Kyiv time.
     *
     * @return the count and the start of the day
     */
    @GetMapping("/api/v1/reviews/today")
    @PreAuthorize(AwardPermissionConstants.CAN_REVIEW)
    public ResponseEntity<ReviewDay> today() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tally.today());
    }
}
