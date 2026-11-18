package ua.edu.chnu.awards.notification.event;

import java.time.Instant;

/**
 * A department with no award this year was reminded; the recipient is told once the surrounding transaction
 * commits.
 *
 * @param userId     the recipient
 * @param email      the recipient's address
 * @param firstName  used in the greeting
 * @param unitId     the department
 * @param unit       English name of the department
 * @param unitUk     Ukrainian name of the department, the English one when it has none
 * @param year       the calendar year without awards
 * @param sender     full name of the person who sent the reminder
 * @param occurredAt when it was sent
 */
public record UnitReminderRequested(Long userId, String email, String firstName, long unitId, String unit,
                                    String unitUk, int year, String sender, Instant occurredAt) {
}
