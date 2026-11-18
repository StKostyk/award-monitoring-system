package ua.edu.chnu.awards.analytics.dto;

import java.time.Instant;

import ua.edu.chnu.awards.award.dto.UserRef;
import ua.edu.chnu.awards.organization.dto.OrganizationRef;

/**
 * A reminder accepted for sending.
 *
 * @param unit      the department
 * @param recipient the colleague told
 * @param sentAt    when it was accepted
 */
public record UnitReminder(OrganizationRef unit, UserRef recipient, Instant sentAt) {
}
