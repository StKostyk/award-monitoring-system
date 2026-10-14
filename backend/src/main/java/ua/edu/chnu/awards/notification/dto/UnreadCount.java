package ua.edu.chnu.awards.notification.dto;

/**
 * How many in-app notifications the caller has not read.
 *
 * @param unread the count
 */
public record UnreadCount(long unread) {
}
