package ua.edu.chnu.awards.common.privacy;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.FieldViolation;

import lombok.RequiredArgsConstructor;

/**
 * The current privacy notice version, the check that a person acknowledged exactly that version and the record of
 * the acknowledgement.
 */
@Component
@RequiredArgsConstructor
public class PrivacyNoticeVersions {

    static final String OUTDATED = "outdated";

    private final PrivacyNoticeProperties properties;
    private final ApplicationEventPublisher events;

    /**
     * The version in force.
     *
     * @return the current version with its publication date
     */
    public PrivacyNoticeProperties.Version current() {
        return properties.noticeVersions().stream()
            .filter(published -> published.version().equals(properties.noticeVersion()))
            .findFirst()
            .orElseThrow();
    }

    /**
     * Every published version.
     *
     * @return the versions, newest first
     */
    public List<PrivacyNoticeProperties.Version> published() {
        return properties.noticeVersions().stream()
            .sorted(Comparator.comparing(PrivacyNoticeProperties.Version::publishedOn).reversed())
            .toList();
    }

    /**
     * Refuses a version other than the current one with 422 {@code validation-failed}, code {@code outdated}.
     *
     * @param version the version the person acknowledged
     * @param field   the request field that carried it
     */
    public void requireCurrent(String version, String field) {
        if (!Objects.equals(version, properties.noticeVersion())) {
            throw ApiProblemException.validationFailed("The privacy notice has changed; read the current version",
                List.of(new FieldViolation(field, OUTDATED,
                    "The current privacy notice version is " + properties.noticeVersion())));
        }
    }

    /**
     * Records an acknowledgement of the current version inside the caller's transaction, so a failure to record it
     * rolls the caller back.
     *
     * @param userId  the person
     * @param version the acknowledged version
     */
    public void recordAcknowledgement(long userId, String version) {
        events.publishEvent(new PrivacyNoticeAcknowledged(userId, version));
    }
}
