package ua.edu.chnu.awards.user.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.user.dto.UserProfileResponse;
import ua.edu.chnu.awards.user.service.UserProfileService;

import lombok.RequiredArgsConstructor;

/**
 * User endpoints.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService profileService;

    /**
     * Profile of the caller.
     *
     * @param jwt the access token
     * @return profile
     */
    @GetMapping("/me")
    public UserProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        return profileService.profileOf(Long.parseLong(jwt.getSubject()));
    }
}
