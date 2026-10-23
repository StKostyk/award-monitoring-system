package ua.edu.chnu.awards.analytics.service;

import java.time.Clock;
import java.time.Year;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.analytics.config.AnalyticsProperties;
import ua.edu.chnu.awards.analytics.dto.AnalyticsTotals;
import ua.edu.chnu.awards.analytics.dto.LevelCount;
import ua.edu.chnu.awards.analytics.dto.PersonalAnalytics;
import ua.edu.chnu.awards.award.dto.CategoryYearCount;
import ua.edu.chnu.awards.award.dto.CountedTotals;
import ua.edu.chnu.awards.award.dto.TimelineEntry;
import ua.edu.chnu.awards.award.dto.YearRange;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.award.service.AwardStatistics;
import ua.edu.chnu.awards.common.web.ApiProblemException;

import lombok.RequiredArgsConstructor;

/**
 * The personal analytics of the caller: approved personal awards in a period of calendar years and the department
 * rank.
 */
@Service
@RequiredArgsConstructor
public class PersonalAnalyticsService {

    private final AwardStatistics statistics;
    private final DepartmentRanking ranking;
    private final AnalyticsProperties properties;
    private final Clock clock;

    /**
     * The analytics of one person.
     *
     * @param userId the caller
     * @param from   first calendar year, by default the year of the first counted award
     * @param to     last calendar year, by default the current year
     * @return the analytics
     * @throws ApiProblemException 400 {@code invalid-parameter} naming {@code from} or {@code to}
     */
    @Transactional(readOnly = true)
    public PersonalAnalytics of(long userId, Integer from, Integer to) {
        YearRange period = period(userId, from, to);
        CountedTotals totals = statistics.totals(userId, period);
        List<CategoryYearCount> counts = totals.approved() == 0 ? List.of()
            : statistics.byCategoryAndYear(userId, period);
        List<LevelCount> byLevel = AwardCountsHelper.byLevel(counts);
        List<TimelineEntry> timeline = totals.approved() == 0 ? List.of()
            : statistics.timeline(userId, period, properties.timelineLimit() + 1);
        boolean truncated = timeline.size() > properties.timelineLimit();
        RecognitionLevel highest = byLevel.isEmpty() ? null : byLevel.getLast().level();
        return new PersonalAnalytics(period,
            new AnalyticsTotals(totals.approved(), totals.verified(), highest, totals.firstAwardDate(),
                totals.latestAwardDate()),
            statistics.openAwards(userId), AwardCountsHelper.byYear(period, counts),
            AwardCountsHelper.byCategory(counts), byLevel,
            truncated ? timeline.subList(0, properties.timelineLimit()) : timeline, truncated,
            ranking.of(userId, totals.approved(), period));
    }

    private YearRange period(long userId, Integer from, Integer to) {
        return PeriodHelper.resolve(from, to, Year.now(clock).getValue(),
            last -> Math.min(statistics.firstYear(userId).orElse(last), last));
    }
}
