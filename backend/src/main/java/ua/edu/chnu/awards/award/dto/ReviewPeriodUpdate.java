package ua.edu.chnu.awards.award.dto;

import java.math.BigDecimal;

/**
 * Body of a change of a faculty's review period.
 *
 * @param workingDays working days per faculty level, null for the global default; read as a decimal so a fraction
 *                    is refused instead of rounded
 */
public record ReviewPeriodUpdate(BigDecimal workingDays) {
}
