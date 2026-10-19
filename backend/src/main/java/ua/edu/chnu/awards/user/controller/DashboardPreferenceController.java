package ua.edu.chnu.awards.user.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.user.dto.DashboardPreferences;
import ua.edu.chnu.awards.user.service.DashboardPreferenceService;

import lombok.RequiredArgsConstructor;

/**
 * The caller's widget choice on the personal dashboard.
 */
@RestController
@RequestMapping("/api/v1/users/me/dashboard-preferences")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class DashboardPreferenceController {

    private final DashboardPreferenceService preferences;
    private final AccessScope access;

    /**
     * The hidden widgets; an empty list until a choice was saved.
     *
     * @return the choice
     */
    @GetMapping
    public ResponseEntity<DashboardPreferences> get() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(preferences.preferences(access.callerId()));
    }

    /**
     * Replaces the hidden widgets.
     *
     * @param request the widgets to hide
     * @return the stored choice
     */
    @PutMapping
    public ResponseEntity<DashboardPreferences> update(@RequestBody DashboardPreferences request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(preferences.update(access.callerId(), request));
    }
}
