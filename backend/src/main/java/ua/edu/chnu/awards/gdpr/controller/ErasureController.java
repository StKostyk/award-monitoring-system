package ua.edu.chnu.awards.gdpr.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.gdpr.dto.ErasureConfirmation;
import ua.edu.chnu.awards.gdpr.dto.ErasureRequest;
import ua.edu.chnu.awards.gdpr.service.ErasureRequestService;

import lombok.RequiredArgsConstructor;

/**
 * The caller's request to erase their account and its cancellation.
 */
@RestController
@RequestMapping("/api/v1/users/me/erasure-request")
@RequiredArgsConstructor
public class ErasureController {

    private final ErasureRequestService erasures;

    /**
     * Schedules the erasure of the caller's account.
     *
     * @param jwt          the access token
     * @param confirmation the current password
     * @return the pending request
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ErasureRequest request(@AuthenticationPrincipal Jwt jwt,
                                  @Valid @RequestBody ErasureConfirmation confirmation) {
        return erasures.request(Long.parseLong(jwt.getSubject()), confirmation.password());
    }

    /**
     * Cancels the caller's pending request.
     *
     * @param jwt the access token
     */
    @PostMapping("/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal Jwt jwt) {
        erasures.cancel(Long.parseLong(jwt.getSubject()));
    }
}
