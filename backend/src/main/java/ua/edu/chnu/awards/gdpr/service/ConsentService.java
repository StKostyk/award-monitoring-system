package ua.edu.chnu.awards.gdpr.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

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
        String version = noticeVersions.current().version();
        return overviewOf(appendIfChanged(userId, new Change(type, version, granted),
            entry -> entry.granted() == granted,
            granted ? AuditAction.CONSENT_GRANTED : AuditAction.CONSENT_WITHDRAWN,
            Map.of("type", type.name(), "version", version)));
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
        return appendIfChanged(userId, new Change(ConsentType.DATA_PROCESSING, version, true),
            entry -> entry.version().equals(version), AuditAction.PRIVACY_NOTICE_ACKNOWLEDGED,
            Map.of("version", version));
    }

    private List<HistoryEntry> appendIfChanged(long userId, Change change, Predicate<HistoryEntry> same,
            AuditAction action, Map<String, Object> details) {
        records.lockOwner(userId);
        List<HistoryEntry> history = new ArrayList<>(records.history(userId));
        boolean unchanged = latest(history, change.type()).map(same::test).orElse(!change.granted());
        if (!unchanged) {
            HistoryEntry entry = new HistoryEntry(change.type(), change.version(), change.granted(),
                Instant.now(clock));
            ClientRequest client = ClientRequest.current();
            records.append(userId, entry, client.address() == null ? null : client.address().getHostAddress(),
                client.userAgent());
            history.add(entry);
            audit.record(action, AuditEntityConstants.GDPR, userId, details);
        }
        return history;
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

    private record Change(ConsentType type, String version, boolean granted) {
    }
}
