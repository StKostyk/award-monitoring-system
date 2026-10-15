package ua.edu.chnu.awards.gdpr.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.common.privacy.PrivacyNoticeProperties;
import ua.edu.chnu.awards.common.privacy.PrivacyNoticeVersions;
import ua.edu.chnu.awards.gdpr.dto.PrivacyNotice;

import lombok.RequiredArgsConstructor;

/**
 * The privacy notice versions, open to everyone.
 */
@RestController
@RequiredArgsConstructor
public class PrivacyNoticeController {

    private final PrivacyNoticeVersions noticeVersions;

    /**
     * The current version and every published version; the texts are static pages of the web client.
     *
     * @return the versions, newest first
     */
    @GetMapping("/api/v1/public/privacy-notice")
    public PrivacyNotice notice() {
        PrivacyNoticeProperties.Version current = noticeVersions.current();
        return new PrivacyNotice(current.version(), current.publishedOn(), noticeVersions.published().stream()
            .map(published -> new PrivacyNotice.Published(published.version(), published.publishedOn()))
            .toList());
    }
}
