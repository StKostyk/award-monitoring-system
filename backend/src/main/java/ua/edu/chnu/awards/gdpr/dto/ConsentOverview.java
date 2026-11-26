package ua.edu.chnu.awards.gdpr.dto;

import java.time.Instant;
import java.util.List;

/**
 * The caller's notice acknowledgement, optional consents and consent history.
 *
 * @param notice   the acknowledgement of the privacy notice
 * @param consents the current state of each optional consent
 * @param history  every consent record, oldest first, without network facts
 */
public record ConsentOverview(Notice notice, List<Consent> consents, List<HistoryEntry> history) {

    public ConsentOverview {
        consents = List.copyOf(consents);
        history = List.copyOf(history);
    }

    /**
     * The acknowledgement of the privacy notice.
     *
     * @param currentVersion          the version in force
     * @param acknowledgedVersion     the last acknowledged version, or null
     * @param acknowledgedAt          when it was acknowledged, or null
     * @param acknowledgementRequired true when the last acknowledged version is not the current one
     */
    public record Notice(String currentVersion, String acknowledgedVersion, Instant acknowledgedAt,
                         boolean acknowledgementRequired) {
    }

    /**
     * The current state of an optional consent.
     *
     * @param type      the consent
     * @param granted   whether it is granted; false when never chosen
     * @param changedAt when it was last chosen, or null
     */
    public record Consent(ConsentType type, boolean granted, Instant changedAt) {
    }

    /**
     * One consent record.
     *
     * @param type    the consent
     * @param version the notice version in force
     * @param granted granted or withdrawn
     * @param at      when it was granted or withdrawn
     */
    public record HistoryEntry(ConsentType type, String version, boolean granted, Instant at) {
    }
}
