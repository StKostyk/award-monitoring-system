package ua.edu.chnu.awards.notification.dto;

/**
 * The channels of one notification type.
 *
 * @param type  the notification type name
 * @param email whether mails of the type are sent
 * @param inApp whether in-app notifications of the type are written
 */
public record NotificationPreferenceItem(String type, Boolean email, Boolean inApp) {
}
