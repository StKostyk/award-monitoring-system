package ua.edu.chnu.awards.award.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.common.web.FieldViolation;

import lombok.RequiredArgsConstructor;

/**
 * Rules of the award date on the {@code Europe/Kyiv} calendar: not in the future, not older than 50 years, and a
 * hint when it lies within the last 30 days.
 */
@Component
@RequiredArgsConstructor
public class AwardDateRules {

    static final String AWARD_DATE = "awardDate";
    static final int MAX_AGE_YEARS = 50;
    static final int RECENT_DAYS = 30;

    private final Clock clock;

    /**
     * Today on the application clock.
     *
     * @return the Kyiv date
     */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /**
     * Refuses a date in the future or more than 50 years before today; exactly 50 years ago is accepted.
     *
     * @param date the award date, may be null
     * @return the refusal, empty when the date is acceptable or not given
     */
    public Optional<FieldViolation> check(LocalDate date) {
        if (date == null) {
            return Optional.empty();
        }
        LocalDate today = today();
        if (date.isAfter(today)) {
            return Optional.of(new FieldViolation(AWARD_DATE, "future", "The award date cannot be in the future"));
        }
        return date.isBefore(today.minusYears(MAX_AGE_YEARS))
            ? Optional.of(new FieldViolation(AWARD_DATE, "too-old", "The award date cannot be more than "
                + MAX_AGE_YEARS + " years ago"))
            : Optional.empty();
    }

    /**
     * Whether the date lies within the last 30 days: today and the 29 days before it.
     *
     * @param date the award date, may be null
     * @return true for a recent date
     */
    public boolean isRecent(LocalDate date) {
        LocalDate today = today();
        return date != null && !date.isAfter(today) && date.isAfter(today.minusDays(RECENT_DAYS));
    }
}
