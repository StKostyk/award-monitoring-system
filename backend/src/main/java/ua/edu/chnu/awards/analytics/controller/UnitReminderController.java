package ua.edu.chnu.awards.analytics.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.analytics.dto.UnitReminder;
import ua.edu.chnu.awards.analytics.dto.UnitReminderRequest;
import ua.edu.chnu.awards.analytics.service.UnitReminderService;

import lombok.RequiredArgsConstructor;

/**
 * The reminder to a department that has no award this year.
 */
@RestController
@RequiredArgsConstructor
public class UnitReminderController {

    private final UnitReminderService reminders;

    /**
     * Reminds a colleague of a department that it has no counted award this year.
     *
     * @param id      the department
     * @param request the colleague
     * @return the accepted reminder
     */
    @PostMapping("/api/v1/analytics/units/{id}/reminders")
    @PreAuthorize("@access.require('analytics:read:department')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public UnitReminder remind(@PathVariable long id, @RequestBody UnitReminderRequest request) {
        return reminders.remind(id, request.recipientId());
    }
}
