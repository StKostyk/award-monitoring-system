package ua.edu.chnu.awards.award.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.config.WorkflowProperties;
import ua.edu.chnu.awards.user.entity.Organization;

import lombok.RequiredArgsConstructor;

/**
 * The timing of an approval request: each level has a number of working days (the faculty's own period at the
 * faculty levels, otherwise the configured default), a request is
 * overdue once the deadline of its current level has passed, and the expected completion counts one period for
 * every level still ahead. A late level is given a fresh period from now, so the estimate moves with the delay.
 * Dates are days of the Kyiv calendar; public holidays are not counted separately.
 */
@Component
@RequiredArgsConstructor
public class StatusEstimator {

    private static final Set<ApprovalLevel> FACULTY_LEVELS =
        EnumSet.of(ApprovalLevel.FACULTY_SECRETARY, ApprovalLevel.DEAN);

    private final Clock clock;
    private final WorkflowProperties properties;
    private final ApprovalPath approvalPath;

    /**
     * The global review period, which the rector's levels always use and a faculty without its own period too.
     *
     * @return working days per level
     */
    public int defaultWorkingDays() {
        return properties.reviewWorkingDays();
    }

    /**
     * The review period a faculty's levels have.
     *
     * @param faculty the faculty, null for a unit outside every faculty
     * @return the faculty's own period, or the global default when it has none
     */
    public int workingDays(Organization faculty) {
        return faculty == null || faculty.getReviewWorkingDays() == null ? defaultWorkingDays()
            : faculty.getReviewWorkingDays();
    }

    /**
     * The review period of one level for an award: the faculty's period at the faculty levels, the global
     * default at the rector's levels.
     *
     * @param award the award, whose organisation decides the faculty
     * @param level the approval level
     * @return working days
     */
    public int workingDays(Award award, ApprovalLevel level) {
        return FACULTY_LEVELS.contains(level) ? workingDays(award.getOrganization().faculty())
            : defaultWorkingDays();
    }

    /**
     * The deadline of a level whose review of an award starts at the given time.
     *
     * @param award the award
     * @param level the level that reviews it from now on
     * @param start when the request reached the level
     * @return the deadline by the level's review period
     */
    public Instant deadline(Award award, ApprovalLevel level, Instant start) {
        return deadline(start, workingDays(award, level));
    }

    /**
     * The deadline of a level with the global review period.
     *
     * @param start when the request reached the level
     * @return the deadline by the global default
     */
    public Instant deadline(Instant start) {
        return deadline(start, defaultWorkingDays());
    }

    /**
     * The deadline of a level whose review starts at the given time.
     *
     * @param start       when the request reached the level
     * @param workingDays the review period
     * @return the same Kyiv time of day after the given number of working days (Monday to Friday); a start on a
     *         weekend counts from the following Monday at midnight
     */
    public Instant deadline(Instant start, int workingDays) {
        ZonedDateTime end = start.atZone(clock.getZone());
        if (isWeekend(end)) {
            end = end.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay(clock.getZone());
        }
        int left = workingDays;
        while (left > 0) {
            end = end.plusDays(1);
            if (!isWeekend(end)) {
                left--;
            }
        }
        return end.toInstant();
    }

    private static boolean isWeekend(ZonedDateTime time) {
        return time.getDayOfWeek() == DayOfWeek.SATURDAY || time.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    /**
     * The levels of the request of an award, by the recognition level of the award's category, with the due date
     * of every level still ahead by that level's review period and the expected completion.
     *
     * @param award   the award
     * @param request its request
     * @return the timeline; without dates for a final or returned request
     */
    public Timeline timeline(Award award, AwardRequest request) {
        RecognitionLevel category = award.getCategory() == null ? null : award.getCategory().getLevel();
        ApprovalLevel current = request.getCurrentLevel();
        List<ApprovalLevel> levels = approvalPath.levels(category, current);
        if (!request.isOpen()) {
            return new Timeline(levels, Map.of(), request.getDeadline(), null, false);
        }
        Instant deadline = request.getDeadline() == null ? deadline(award, current, request.getSubmittedAt())
            : request.getDeadline();
        Instant now = clock.instant();
        boolean overdue = now.isAfter(deadline);
        Instant end = overdue ? deadline(award, current, now) : deadline;
        Map<ApprovalLevel, LocalDate> due = new EnumMap<>(ApprovalLevel.class);
        due.put(current, date(end));
        for (ApprovalLevel level : levels.subList(levels.indexOf(current) + 1, levels.size())) {
            end = deadline(award, level, end);
            due.put(level, date(end));
        }
        return new Timeline(levels, due, deadline, date(end), overdue);
    }

    private LocalDate date(Instant instant) {
        return LocalDate.ofInstant(instant, clock.getZone());
    }

    /**
     * The timing of a request.
     *
     * @param levels              the approval path, lowest first
     * @param due                 Kyiv due date of the current level, revised when overdue, and every level after it
     * @param deadline            deadline of the current level, derived from the submission when none is stored;
     *                            the stored value for a request no level is reviewing
     * @param estimatedCompletion Kyiv date the last level is expected to finish, null for a request no level is
     *                            reviewing
     * @param overdue             whether the current level is past its deadline
     */
    public record Timeline(List<ApprovalLevel> levels, Map<ApprovalLevel, LocalDate> due, Instant deadline,
                           LocalDate estimatedCompletion, boolean overdue) {

        /**
         * Keeps unmodifiable copies of the levels and the due dates.
         */
        public Timeline {
            levels = List.copyOf(levels);
            due = Map.copyOf(due);
        }
    }
}
