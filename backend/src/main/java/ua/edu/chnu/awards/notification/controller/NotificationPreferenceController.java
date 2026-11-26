package ua.edu.chnu.awards.notification.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.notification.dto.NotificationPreferences;
import ua.edu.chnu.awards.notification.service.NotificationPreferenceService;

import lombok.RequiredArgsConstructor;

/**
 * The caller's notification channels per type; security and privacy mails have no preference.
 */
@RestController
@RequestMapping("/api/v1/users/me/notification-preferences")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferences;
    private final AccessScope access;

    /**
     * Every type with its channels.
     *
     * @return all types
     */
    @GetMapping
    public ResponseEntity<NotificationPreferences> get() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new NotificationPreferences(preferences.preferences(access.callerId())));
    }

    /**
     * Stores the listed types; types not listed keep their state.
     *
     * @param request the types to store
     * @return all types after the change
     */
    @PutMapping
    public ResponseEntity<NotificationPreferences> update(@RequestBody NotificationPreferences request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new NotificationPreferences(preferences.update(access.callerId(), request.preferences())));
    }
}
