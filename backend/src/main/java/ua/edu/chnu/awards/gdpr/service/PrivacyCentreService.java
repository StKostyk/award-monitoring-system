package ua.edu.chnu.awards.gdpr.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.common.web.PageResponse;
import ua.edu.chnu.awards.gdpr.dto.ActivityEntry;
import ua.edu.chnu.awards.gdpr.dto.PrivacySummary;
import ua.edu.chnu.awards.gdpr.repository.ActivityQueries;
import ua.edu.chnu.awards.gdpr.repository.PrivacySummaryQueries;

import lombok.RequiredArgsConstructor;

/**
 * The read side of the privacy centre: what a person shares and what was recorded about their account.
 */
@Service
@RequiredArgsConstructor
public class PrivacyCentreService {

    /** Award visibilities, each reported even without awards. */
    public static final List<String> VISIBILITIES = List.of("PRIVATE", "UNIVERSITY", "PUBLIC");

    private final PrivacySummaryQueries summaryQueries;
    private final ActivityQueries activityQueries;

    /**
     * What the person shares and when the data was last exported.
     *
     * @param userId the person
     * @return the summary
     */
    @Transactional(readOnly = true)
    public PrivacySummary summary(long userId) {
        Map<String, Long> stored = summaryQueries.awardsByVisibility(userId);
        Map<String, Long> awards = new HashMap<>();
        VISIBILITIES.forEach(visibility -> awards.put(visibility, stored.getOrDefault(visibility, 0L)));
        return new PrivacySummary(awards, summaryQueries.documentsShown(userId),
            summaryQueries.emailOffTypes(userId), summaryQueries.lastExportAt(userId));
    }

    /**
     * One page of the person's activity, newest first.
     *
     * @param userId the person
     * @param page   0-based page, a negative one is the first
     * @param size   page size, kept between 1 and the list maximum
     * @return the page
     */
    @Transactional(readOnly = true)
    public PageResponse<ActivityEntry> activity(long userId, int page, int size) {
        return PageResponse.of(activityQueries.activity(userId, PageResponse.request(page, size, Sort.unsorted())));
    }
}
