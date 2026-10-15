package ua.edu.chnu.awards.gdpr.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.common.privacy.PrivacyNoticeAcknowledged;
import ua.edu.chnu.awards.common.privacy.PrivacyNoticeVersions;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.ClientRequest;
import ua.edu.chnu.awards.common.web.FieldViolation;
import ua.edu.chnu.awards.gdpr.dto.ConsentOverview;
import ua.edu.chnu.awards.gdpr.dto.ConsentOverview.HistoryEntry;
import ua.edu.chnu.awards.gdpr.dto.ConsentType;
import ua.edu.chnu.awards.gdpr.repository.ConsentRecords;

import lombok.RequiredArgsConstructor;

/**
 * The privacy notice acknowledgement and the optional consents of a person, kept as an append-only history.
 */
@Service
@RequiredArgsConstructor
public class ConsentService {

    private static final List<ConsentType> OPTIONAL = List.of(ConsentType.ANALYTICS);

    private final ConsentRecords records;
    private final PrivacyNoticeVersions noticeVersions;
    private final AuditService audit;
    private final Clock clock;

    /**
     * The person's acknowledgement, optional consents and history.
     *
     * @param userId the person
     * @return the overview
     */
    @Transactional(readOnly = true)
    public ConsentOverview overview(long userId) {
        return overviewOf(records.history(userId));
    }

    /**
     * Acknowledges the current privacy notice; an acknowledged version appends nothing.
     *
     * @param userId  the person
     * @param version the version the person read
     * @return the overview after the acknowledgement
     */
    @Transactional
    public ConsentOverview acknowledge(long userId, String version) {
        noticeVersions.requireCurrent(version, "version");
        return overviewOf(appendAcknowledgement(userId, version));
    }

    /**
     * Grants or withdraws an optional consent; the unchanged state appends nothing.
     *
     * @param userId  the person
     * @param type    the consent
     * @param granted true to grant, false to withdraw
     * @return the overview after the change
     */
    @Transactional
    public ConsentOverview choose(long userId, ConsentType type, boolean granted) {
        if (!OPTIONAL.contains(type)) {
            throw ApiProblemException.validationFailed("Only optional consents can be granted or withdrawn",
                List.of(new FieldViolation("type", "not-optional",
                    "The acknowledgement of the privacy notice cannot be withdrawn")));
        }
        records.lockOwner(userId);
        List<HistoryEntry> history = new ArrayList<>(records.history(userId));
        boolean current = latest(history, type).map(HistoryEntry::granted).orElse(false);
        if (current != granted) {
            String version = noticeVersions.current().version();
            history.add(append(userId, new HistoryEntry(type, version, granted, Instant.now(clock))));
            audit.record(granted ? AuditAction.CONSENT_GRANTED : AuditAction.CONSENT_WITHDRAWN,
                AuditEntityConstants.GDPR, userId, Map.of("type", type.name(), "version", version));
        }
        return overviewOf(history);
    }

    /**
     * Records an acknowledgement given elsewhere, such as at registration, inside the caller's transaction.
     *
     * @param event the acknowledgement
     */
    @EventListener
    public void onPrivacyNoticeAcknowledged(PrivacyNoticeAcknowledged event) {
        appendAcknowledgement(event.userId(), event.version());
    }

    private List<HistoryEntry> appendAcknowledgement(long userId, String version) {
        records.lockOwner(userId);
        List<HistoryEntry> history = new ArrayList<>(records.history(userId));
        boolean acknowledged = latest(history, ConsentType.DATA_PROCESSING)
            .filter(entry -> entry.version().equals(version))
            .isPresent();
        if (!acknowledged) {
            history.add(append(userId, new HistoryEntry(ConsentType.DATA_PROCESSING, version, true,
                Instant.now(clock))));
            audit.record(AuditAction.PRIVACY_NOTICE_ACKNOWLEDGED, AuditEntityConstants.GDPR, userId,
                Map.of("version", version));
        }
        return history;
    }

    private HistoryEntry append(long userId, HistoryEntry entry) {
        ClientRequest client = ClientRequest.current();
        records.append(userId, entry, client.address() == null ? null : client.address().getHostAddress(),
            client.userAgent());
        return entry;
    }

    private ConsentOverview overviewOf(List<HistoryEntry> history) {
        String current = noticeVersions.current().version();
        Optional<HistoryEntry> acknowledged = latest(history, ConsentType.DATA_PROCESSING);
        ConsentOverview.Notice notice = new ConsentOverview.Notice(current,
            acknowledged.map(HistoryEntry::version).orElse(null), acknowledged.map(HistoryEntry::at).orElse(null),
            acknowledged.filter(entry -> entry.version().equals(current)).isEmpty());
        List<ConsentOverview.Consent> consents = OPTIONAL.stream()
            .map(type -> latest(history, type)
                .map(entry -> new ConsentOverview.Consent(type, entry.granted(), entry.at()))
                .orElseGet(() -> new ConsentOverview.Consent(type, false, null)))
            .toList();
        return new ConsentOverview(notice, consents, history);
    }

    private static Optional<HistoryEntry> latest(List<HistoryEntry> history, ConsentType type) {
        return history.reversed().stream().filter(entry -> entry.type() == type).findFirst();
    }
}
