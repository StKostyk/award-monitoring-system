package ua.edu.chnu.awards.auth.web;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.auth.dto.EmailChangeRequest;
import ua.edu.chnu.awards.auth.service.EmailChangeService;

import lombok.RequiredArgsConstructor;

/**
 * Moving the caller's own account to another sign-in address.
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class EmailChangeController {

    private final EmailChangeService emailChangeService;

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
