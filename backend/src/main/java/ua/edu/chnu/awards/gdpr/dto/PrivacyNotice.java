package ua.edu.chnu.awards.gdpr.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * The privacy notice version in force and every published version.
 *
 * @param version     the current version
 * @param publishedOn when the current version took effect
 * @param versions    every published version, newest first
 */
public record PrivacyNotice(String version, LocalDate publishedOn, List<Published> versions) {

    public PrivacyNotice {
        versions = List.copyOf(versions);
    }

    /**
     * One published version.
     *
     * @param version     the version label
     * @param publishedOn when it took effect
     */
    public record Published(String version, LocalDate publishedOn) {
    }
}
