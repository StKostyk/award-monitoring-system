package ua.edu.chnu.awards.notification.controller;

import java.util.List;
import java.util.Objects;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.common.web.PageResponse;
import ua.edu.chnu.awards.notification.dto.MarkedCount;
import ua.edu.chnu.awards.notification.dto.NotificationResponse;
import ua.edu.chnu.awards.notification.dto.UnreadCount;
import ua.edu.chnu.awards.notification.entity.NotificationType;
import ua.edu.chnu.awards.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;

/**
 * The caller's in-app notifications; never cached, so the badge follows every read.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notifications;
    private final AccessScope access;

    /**
     * The caller's notifications, newest first.
     *
     * @param unread only unread ones
     * @param type   only these types, every type when absent
     * @param page   0-based page
     * @param size   page size, capped at 100
     * @return the page
     */
    @GetMapping
    public ResponseEntity<PageResponse<NotificationResponse>> list(
            @RequestParam(defaultValue = "false") boolean unread,
            @RequestParam(required = false) List<NotificationType> type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<NotificationType> types = type == null ? List.of() : type.stream().filter(Objects::nonNull).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(PageResponse.of(notifications.list(access.callerId(), unread, types, page, size)));
    }

    /**
     * How many of the caller's notifications are unread.
     *
     * @return the count
     */
    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCount> unreadCount() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new UnreadCount(notifications.unreadCount(access.callerId())));
    }

    /**
     * Marks one of the caller's notifications read; repeating it changes nothing.
     *
     * @param id the notification
     * @return 204
     */
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable long id) {
        notifications.markRead(access.callerId(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Marks one of the caller's notifications unread; repeating it changes nothing.
     *
     * @param id the notification
     * @return 204
     */
    @DeleteMapping("/{id}/read")
    public ResponseEntity<Void> markUnread(@PathVariable long id) {
        notifications.markUnread(access.callerId(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Marks all of the caller's notifications read.
     *
     * @return how many were marked
     */
    @PostMapping("/read-all")
    public MarkedCount markAllRead() {
        return new MarkedCount(notifications.markAllRead(access.callerId()));
    }
}
