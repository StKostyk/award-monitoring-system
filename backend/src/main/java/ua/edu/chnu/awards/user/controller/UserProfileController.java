package ua.edu.chnu.awards.user.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.user.dto.UserProfileResponse;
import ua.edu.chnu.awards.user.dto.UserUpdateRequest;
import ua.edu.chnu.awards.user.service.UserProfileService;

import lombok.RequiredArgsConstructor;

/**
 * The caller's own profile: reading it and correcting the names.
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService profileService;

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
}
