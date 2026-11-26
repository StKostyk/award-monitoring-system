package ua.edu.chnu.awards.common.privacy;

import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The privacy notice versions; the texts are static pages of the web client per version and language.
 *
 * @param noticeVersion  the version in force, which must be one of the published versions
 * @param noticeVersions every published version with its publication date
 */
@ConfigurationProperties(prefix = "app.privacy")
public record PrivacyNoticeProperties(String noticeVersion, List<Version> noticeVersions) {

    public PrivacyNoticeProperties {
        noticeVersions = noticeVersions == null ? List.of() : List.copyOf(noticeVersions);
        String current = noticeVersion;
        if (noticeVersions.stream().noneMatch(published -> published.version().equals(current))) {
            throw new IllegalStateException("app.privacy.notice-version " + noticeVersion
                + " is not listed in app.privacy.notice-versions");
        }
    }

    /**
     * One published version of the notice.
     *
     * @param version     the version label
     * @param publishedOn the day the version took effect
     */
    public record Version(String version, LocalDate publishedOn) {
    }
}
