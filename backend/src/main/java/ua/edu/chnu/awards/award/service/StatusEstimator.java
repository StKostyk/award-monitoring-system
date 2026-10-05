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
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.config.WorkflowProperties;

import lombok.RequiredArgsConstructor;

/**
 * The timing of an approval request: each level has the configured number of working days, a request is
 * overdue once the deadline of its current level has passed, and the expected completion counts one period for
 * every level still ahead. A late level is given a fresh period from now, so the estimate moves with the delay.
 * Dates are days of the Kyiv calendar; public holidays are not counted separately.
 */
@Component
@RequiredArgsConstructor
public class StatusEstimator {

    private static final Set<RequestStatus> ACTIVE =
        EnumSet.of(RequestStatus.SUBMITTED, RequestStatus.IN_REVIEW, RequestStatus.ESCALATED);

    private final Clock clock;
    private final WorkflowProperties properties;
    private final ApprovalPath approvalPath;

    /**
     * The deadline of a level whose review starts at the given time.
     *
     * @param start when the request reached the level
     * @return the same Kyiv time of day after the review period's number of working days (Monday to Friday);
     *         a start on a weekend counts from the following Monday at midnight
     */
    public Instant deadline(Instant start) {
        ZonedDateTime end = start.atZone(clock.getZone());
        if (isWeekend(end)) {
            end = end.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay(clock.getZone());
        }
        int left = properties.reviewWorkingDays();
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
     * Whether a request is waiting for a reviewer: neither final nor returned to its owner.
     *
     * @param request the request
     * @return true while a level has to review it
     */
    public boolean isActive(AwardRequest request) {
        return ACTIVE.contains(request.getStatus());
    }

    /**
     * The timeline of the request of an award, by the recognition level of the award's category.
     *
     * @param award   the award
     * @param request its request
     * @return the timeline
     */
    public Timeline timeline(Award award, AwardRequest request) {
        return timeline(request, award.getCategory() == null ? null : award.getCategory().getLevel());
    }

    /**
     * The levels of a request with the due date of every level still ahead and the expected completion.
     *
     * @param request  the request
     * @param category the recognition level of the award's category, null when the award has none
     * @return the timeline; without dates for a final or returned request
     */
    public Timeline timeline(AwardRequest request, RecognitionLevel category) {
        List<ApprovalLevel> levels = approvalPath.levels(category, request.getCurrentLevel());
        if (!isActive(request)) {
            return new Timeline(levels, Map.of(), request.getDeadline(), null, false);
        }
        Instant deadline = request.getDeadline() == null ? deadline(request.getSubmittedAt())
            : request.getDeadline();
        Instant now = clock.instant();
        boolean overdue = now.isAfter(deadline);
        Instant end = overdue ? deadline(now) : deadline;
        Map<ApprovalLevel, LocalDate> due = new EnumMap<>(ApprovalLevel.class);
        due.put(request.getCurrentLevel(), date(end));
        for (ApprovalLevel level : levels.subList(levels.indexOf(request.getCurrentLevel()) + 1, levels.size())) {
            end = deadline(end);
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
