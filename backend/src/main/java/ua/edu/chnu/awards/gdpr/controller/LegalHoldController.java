package ua.edu.chnu.awards.gdpr.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.gdpr.dto.LegalHold;
import ua.edu.chnu.awards.gdpr.dto.LegalHoldOverview;
import ua.edu.chnu.awards.gdpr.dto.LegalHoldReason;
import ua.edu.chnu.awards.gdpr.service.LegalHoldService;

import lombok.RequiredArgsConstructor;

/**
 * Legal holds on an account, for holders of {@code legal-hold:manage}.
 */
@RestController
@RequestMapping("/api/v1/users/{id}/legal-holds")
@RequiredArgsConstructor
public class LegalHoldController {

    private static final String CAN_MANAGE = "@access.require('legal-hold:manage')";

    private final LegalHoldService legalHoldService;

    /**
     * Every hold of the account.
     *
     * @param id the account
     * @return the holds, newest first
     */
    @GetMapping
    @PreAuthorize(CAN_MANAGE)
    public LegalHoldOverview list(@PathVariable long id) {
        return legalHoldService.overview(id);
    }

    /**
     * Places a hold on the account.
     *
     * @param jwt  the access token
     * @param id   the account
     * @param body why
     * @return the new hold
     */
    @PostMapping
    @PreAuthorize(CAN_MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    public LegalHold place(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                           @RequestBody LegalHoldReason body) {
        return legalHoldService.place(id, body.reason(), Long.parseLong(jwt.getSubject()));
    }

    /**
     * Lifts a hold of the account.
     *
     * @param jwt    the access token
     * @param id     the account
     * @param holdId the hold
     * @param body   why
     * @return the lifted hold
     */
    @PostMapping("/{holdId}/lift")
    @PreAuthorize(CAN_MANAGE)
    public LegalHold lift(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @PathVariable long holdId,
                          @RequestBody LegalHoldReason body) {
        return legalHoldService.lift(id, holdId, body.reason(), Long.parseLong(jwt.getSubject()));
    }
}
