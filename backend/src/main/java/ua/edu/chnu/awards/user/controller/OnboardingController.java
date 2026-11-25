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
import ua.edu.chnu.awards.user.dto.OnboardingRequest;
import ua.edu.chnu.awards.user.dto.OnboardingState;
import ua.edu.chnu.awards.user.service.OnboardingService;

import lombok.RequiredArgsConstructor;

/**
 * The caller's getting-started steps.
 */
@RestController
@RequestMapping("/api/v1/users/me/onboarding")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboarding;
    private final AccessScope access;

    /**
     * The steps of the caller's roles in order with their state.
     *
     * @return the steps
     */
    @GetMapping
    public ResponseEntity<OnboardingState> get() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(onboarding.state(access.callerId()));
    }

    /**
     * Replaces the stored progress.
     *
     * @param request the steps marked done, the hidden checklist and the seen tour
     * @return the steps after the save
     */
    @PutMapping
    public ResponseEntity<OnboardingState> save(@RequestBody OnboardingRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(onboarding.save(access.callerId(), request));
    }
}
