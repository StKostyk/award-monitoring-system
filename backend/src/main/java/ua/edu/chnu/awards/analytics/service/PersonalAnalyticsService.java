package ua.edu.chnu.awards.analytics.service;

import java.time.Clock;
import java.time.Year;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
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

    private static final int FIRST_YEAR = 1950;
    private static final int LAST_YEAR = 2100;
    private static final String FROM = "from";
    private static final String TO = "to";

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
        requireYear(from, FROM);
        requireYear(to, TO);
        int last = to == null ? Year.now(clock).getValue() : to;
        int first = from == null ? Math.min(statistics.firstYear(userId).orElse(last), last) : from;
        if (first > last) {
            throw invalid(FROM, "The first year must not lie after the last year " + last);
        }
        return new YearRange(first, last);
    }

    private static void requireYear(Integer year, String parameter) {
        if (year != null && (year < FIRST_YEAR || year > LAST_YEAR)) {
            throw invalid(parameter, "The year must lie between " + FIRST_YEAR + " and " + LAST_YEAR);
        }
    }

    private static ApiProblemException invalid(String parameter, String detail) {
        return new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-parameter", detail,
            Map.of("parameter", parameter));
    }
}
