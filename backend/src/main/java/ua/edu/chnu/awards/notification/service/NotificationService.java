package ua.edu.chnu.awards.notification.service;

import java.time.Clock;
import java.util.Collection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.common.web.PageResponse;
import ua.edu.chnu.awards.notification.dto.NotificationResponse;
import ua.edu.chnu.awards.notification.entity.Notification;
import ua.edu.chnu.awards.notification.entity.NotificationChannel;
import ua.edu.chnu.awards.notification.entity.NotificationType;
import ua.edu.chnu.awards.notification.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;

/**
 * A person's in-app notifications: newest first, the unread count and the read marks. Other people's rows and
 * e-mail log rows are never visible.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final NotificationChannel IN_APP = NotificationChannel.IN_APP;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("sentAt"), Sort.Order.desc("id"));

    private final NotificationRepository notifications;
    private final Clock clock;

    /**
     * One page of the person's notifications.
     *
     * @param userId the person
     * @param unread only unread ones
     * @param types  only these types, every type when empty
     * @param page   0-based page, negative read as 0
     * @param size   page size, clamped to 1-100
     * @return the page, newest first
     */
    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(long userId, boolean unread, Collection<NotificationType> types, int page,
                                           int size) {
        Pageable pageable = PageResponse.request(page, size, NEWEST_FIRST);
        Page<Notification> rows;
        if (types.isEmpty()) {
            rows = unread
                ? notifications.findByUserIdAndChannelAndReadFalse(userId, IN_APP, pageable)
                : notifications.findByUserIdAndChannel(userId, IN_APP, pageable);
        } else {
            rows = unread
                ? notifications.findByUserIdAndChannelAndReadFalseAndTypeIn(userId, IN_APP, types, pageable)
                : notifications.findByUserIdAndChannelAndTypeIn(userId, IN_APP, types, pageable);
        }
        return rows.map(NotificationResponse::of);
    }

    /**
     * How many of the person's notifications are unread.
     *
     * @param userId the person
     * @return the count
     */
    @Transactional(readOnly = true)
    public long unreadCount(long userId) {
        return notifications.countByUserIdAndChannelAndReadFalse(userId, IN_APP);
    }

    /**
     * Marks one of the person's notifications read; one read already stays as it is.
     *
     * @param userId the person
     * @param id     the notification
     * @throws NotificationNotFoundException when it is not the person's in-app notification
     */
    @Transactional
    public void markRead(long userId, long id) {
        if (notifications.markRead(id, userId, IN_APP, clock.instant()) == 0
                && !notifications.existsByIdAndUserIdAndChannel(id, userId, IN_APP)) {
            throw new NotificationNotFoundException(id);
        }
    }

    /**
     * Marks one of the person's notifications unread; one unread already stays as it is.
     *
     * @param userId the person
     * @param id     the notification
     * @throws NotificationNotFoundException when it is not the person's in-app notification
     */
    @Transactional
    public void markUnread(long userId, long id) {
        if (notifications.markUnread(id, userId, IN_APP) == 0
                && !notifications.existsByIdAndUserIdAndChannel(id, userId, IN_APP)) {
            throw new NotificationNotFoundException(id);
        }
    }

    /**
     * Marks all of the person's unread notifications read.
     *
     * @param userId the person
     * @return how many were marked
     */
    @Transactional
    public int markAllRead(long userId) {
        return notifications.markAllRead(userId, IN_APP, clock.instant());
    }
}
