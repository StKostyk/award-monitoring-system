package ua.edu.chnu.awards.analytics.dto;

/**
 * The colleague of a department who receives its reminder.
 *
 * @param recipientId the colleague
 */
public record UnitReminderRequest(Long recipientId) {
}
