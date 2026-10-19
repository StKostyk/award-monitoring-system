package ua.edu.chnu.awards.analytics.dto;

import java.util.List;

import ua.edu.chnu.awards.award.dto.OpenAwards;
import ua.edu.chnu.awards.award.dto.TimelineEntry;
import ua.edu.chnu.awards.award.dto.YearRange;

/**
 * The approved personal awards of the caller in a period, with the department rank.
 *
 * @param period            the calendar years
 * @param totals            totals of the counted awards
 * @param inProgress        own drafts and pending awards, whatever the period
 * @param byYear            every year of the period in order
 * @param byCategory        highest count first, then by English name
 * @param byLevel           non-zero levels in level order
 * @param timeline          newest award date first
 * @param timelineTruncated whether older awards were left off the timeline
 * @param rank              the department rank
 */
public record PersonalAnalytics(YearRange period, AnalyticsTotals totals, OpenAwards inProgress,
                                List<YearCount> byYear, List<CategoryCount> byCategory, List<LevelCount> byLevel,
                                List<TimelineEntry> timeline, boolean timelineTruncated, PersonalRank rank) {

    /**
     * Copies the lists.
     *
     * @param period            the calendar years
     * @param totals            totals of the counted awards
     * @param inProgress        own drafts and pending awards
     * @param byYear            every year of the period
     * @param byCategory        counts per category
     * @param byLevel           counts per level
     * @param timeline          the counted awards
     * @param timelineTruncated whether older awards were left off
     * @param rank              the department rank
     */
    public PersonalAnalytics {
        byYear = List.copyOf(byYear);
        byCategory = List.copyOf(byCategory);
        byLevel = List.copyOf(byLevel);
        timeline = List.copyOf(timeline);
    }
}
