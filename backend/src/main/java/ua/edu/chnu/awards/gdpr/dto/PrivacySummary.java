package ua.edu.chnu.awards.gdpr.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * What the caller currently shares and when the data was last exported.
 *
 * @param awardsByVisibility own approved personal awards per visibility, every visibility present
 * @param documentsShown     own documents shown to colleagues
 * @param emailOffTypes      notification types with e-mail switched off
 * @param lastExportAt       the last data export, or null
 */
public record PrivacySummary(Map<String, Long> awardsByVisibility, long documentsShown, List<String> emailOffTypes,
                             Instant lastExportAt) {

    public PrivacySummary {
        awardsByVisibility = Map.copyOf(awardsByVisibility);
        emailOffTypes = List.copyOf(emailOffTypes);
    }
}
