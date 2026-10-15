package ua.edu.chnu.awards.gdpr.controller;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.gdpr.dto.ConsentChoice;
import ua.edu.chnu.awards.gdpr.dto.ConsentOverview;
import ua.edu.chnu.awards.gdpr.dto.ConsentType;
import ua.edu.chnu.awards.gdpr.dto.NoticeAcknowledgement;
import ua.edu.chnu.awards.gdpr.service.ConsentService;

import lombok.RequiredArgsConstructor;

/**
 * The caller's privacy notice acknowledgement and optional consents.
 */
@RestController
@RequestMapping("/api/v1/users/me/consents")
@RequiredArgsConstructor
public class ConsentController {

    private final ConsentService consentService;

    /**
     * The caller's acknowledgement, optional consents and consent history.
     *
     * @param jwt the access token
     * @return the overview
     */
    @GetMapping
    public ConsentOverview overview(@AuthenticationPrincipal Jwt jwt) {
        return consentService.overview(Long.parseLong(jwt.getSubject()));
    }

    /**
     * Acknowledges the current privacy notice.
     *
     * @param jwt  the access token
     * @param body the version the caller read
     * @return the overview after the acknowledgement
     */
    @PostMapping("/notice-acknowledgement")
    public ConsentOverview acknowledge(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody NoticeAcknowledgement body) {
        return consentService.acknowledge(Long.parseLong(jwt.getSubject()), body.version());
    }

    /**
     * Grants or withdraws an optional consent.
     *
     * @param jwt  the access token
     * @param type the consent
     * @param body the choice
     * @return the overview after the change
     */
    @PutMapping("/{type}")
    public ConsentOverview choose(@AuthenticationPrincipal Jwt jwt, @PathVariable ConsentType type,
                                  @Valid @RequestBody ConsentChoice body) {
        return consentService.choose(Long.parseLong(jwt.getSubject()), type, body.granted());
    }
}
