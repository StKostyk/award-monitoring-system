package ua.edu.chnu.awards.user.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.auth.dto.EmailChangeRequest;
import ua.edu.chnu.awards.auth.service.EmailChangeService;
import ua.edu.chnu.awards.user.dto.UserProfileResponse;
import ua.edu.chnu.awards.user.dto.UserUpdateRequest;
import ua.edu.chnu.awards.user.service.UserProfileService;

import lombok.RequiredArgsConstructor;

/**
 * The caller's own profile: reading it, correcting the names and moving to another sign-in address.
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService profileService;
    private final EmailChangeService emailChangeService;

    /**
     * Profile of the caller.
     *
     * @param jwt the access token
     * @return profile
     */
    @GetMapping
    public UserProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        return profileService.profileOf(Long.parseLong(jwt.getSubject()));
    }

    /**
     * Corrects the caller's first and last name; any other property of the body is refused.
     *
     * @param jwt     the access token
     * @param request the names to set
     * @return the profile after the change
     */
    @PatchMapping
    public UserProfileResponse update(@AuthenticationPrincipal Jwt jwt, @RequestBody UserUpdateRequest request) {
        return profileService.update(Long.parseLong(jwt.getSubject()), request);
    }

    /**
     * Starts a change of the caller's sign-in address; the link goes to the new address.
     *
     * @param jwt     the access token
     * @param request the new address and the current password
     */
    @PostMapping("/email-change")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestEmailChange(@AuthenticationPrincipal Jwt jwt,
                                   @Valid @RequestBody EmailChangeRequest request) {
        emailChangeService.request(Long.parseLong(jwt.getSubject()), request.newEmail(), request.currentPassword());
    }
}
