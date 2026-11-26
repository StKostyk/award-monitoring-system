package ua.edu.chnu.awards.award.dto;

/**
 * The review period of a faculty.
 *
 * @param organizationId       the faculty
 * @param workingDays          the faculty's own period, null when it uses the global default
 * @param effectiveWorkingDays the period its levels have now
 * @param defaultWorkingDays   the global default
 * @param updatable            whether the caller may change it
 */
public record ReviewPeriodResponse(long organizationId, Integer workingDays, int effectiveWorkingDays,
                                   int defaultWorkingDays, boolean updatable) {
}
