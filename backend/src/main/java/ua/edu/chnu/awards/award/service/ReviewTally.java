package ua.edu.chnu.awards.award.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.dto.ReviewDay;
import ua.edu.chnu.awards.award.repository.ReviewDecisionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Counts the decisions the caller made today, from 00:00 Kyiv time.
 */
@Component
@RequiredArgsConstructor
public class ReviewTally {

    private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");

    private final ReviewDecisionRepository decisions;
    private final AccessScope access;
    private final Clock clock;

    /**
     * The caller's decisions of the current Kyiv day, under an own or a delegated role.
     *
     * @return the count and the start of the day
     */
    @Transactional(readOnly = true)
    public ReviewDay today() {
        Instant since = LocalDate.now(clock.withZone(KYIV)).atStartOfDay(KYIV).toInstant();
        return new ReviewDay(decisions.countByReviewerIdAndDecidedAtGreaterThanEqual(access.callerId(), since),
            since);
    }
}
