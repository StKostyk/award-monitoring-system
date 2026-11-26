package ua.edu.chnu.awards.notification.service;

import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.common.web.ApiProblemException;

/**
 * An in-app notification that does not exist or belongs to somebody else: 404 {@code notification-not-found}.
 */
public class NotificationNotFoundException extends ApiProblemException {

    private static final long serialVersionUID = 1L;

    /**
     * The problem for one notification.
     *
     * @param id the notification
     */
    public NotificationNotFoundException(long id) {
        super(HttpStatus.NOT_FOUND, "notification-not-found", "Notification " + id + " not found");
    }
}
