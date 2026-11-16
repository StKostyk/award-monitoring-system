package ua.edu.chnu.awards.award.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Links to award pages of the browser application, for mails.
 */
@Component
public class AwardLinks {

    private final String frontendUrl;

    public AwardLinks(@Value("${app.auth.frontend-url}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    /**
     * The browser-application page of one award.
     *
     * @param awardId the award
     * @return absolute URL
     */
    public String awardLink(long awardId) {
        return frontendUrl + "/awards/" + awardId;
    }

    /**
     * The registration page with the address filled in.
     *
     * @param email the address
     * @return the absolute link
     */
    public String registrationLink(String email) {
        return frontendUrl + "/register?email=" + URLEncoder.encode(email, StandardCharsets.UTF_8);
    }

    /**
     * The privacy notice.
     *
     * @return the absolute link
     */
    public String privacyNoticeLink() {
        return frontendUrl + "/privacy-notice";
    }
}
