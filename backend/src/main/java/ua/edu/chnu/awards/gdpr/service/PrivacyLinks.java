package ua.edu.chnu.awards.gdpr.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Links of privacy mails into the browser application.
 */
@Component
public class PrivacyLinks {

    private final String frontendUrl;

    /**
     * Creates the links.
     *
     * @param frontendUrl where the browser application lives
     */
    public PrivacyLinks(@Value("${app.auth.frontend-url}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    /**
     * The privacy centre.
     *
     * @return its address
     */
    public String privacyCentre() {
        return frontendUrl + "/privacy";
    }

    /**
     * The compliance page of the data protection officer.
     *
     * @return its address
     */
    public String compliance() {
        return frontendUrl + "/compliance";
    }
}
