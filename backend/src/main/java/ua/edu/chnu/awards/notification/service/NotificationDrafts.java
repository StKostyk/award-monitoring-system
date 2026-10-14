package ua.edu.chnu.awards.notification.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.award.event.AwardCorrected;
import ua.edu.chnu.awards.award.event.AwardDecided;
import ua.edu.chnu.awards.award.event.OverdueNoticed;
import ua.edu.chnu.awards.award.event.ReviewTakenOver;
import ua.edu.chnu.awards.delegation.event.DelegationCreated;
import ua.edu.chnu.awards.delegation.event.DelegationRevoked;
import ua.edu.chnu.awards.notification.entity.NotificationType;
import ua.edu.chnu.awards.user.event.RoleAssigned;
import ua.edu.chnu.awards.user.event.RoleRevoked;

/**
 * Turns committed events into notifications, one per recipient, with only the values the sentence needs: never
 * the reviewer's comment or the corrected values.
 */
@Component
public class NotificationDrafts {

    static final String AWARD = "AWARD";

    private static final String OTHER_PARTY = "otherParty";

    /**
     * The owner's notification of a decision.
     *
     * @param event the decision
     * @return one draft
     */
    public NotificationDraft of(AwardDecided event) {
        Map<String, Object> payload = award(event.title(), event.titleUk(), event.reviewer());
        payload.put("level", event.level().name());
        return new NotificationDraft(event.userId(), typeOf(event), payload, AWARD, event.awardId(),
            event.occurredAt());
    }

    /**
     * The owner's notification of a correction, naming the changed fields only.
     *
     * @param event the correction
     * @return one draft
     */
    public NotificationDraft of(AwardCorrected event) {
        Map<String, Object> payload = award(event.title(), event.titleUk(), event.reviewer());
        payload.put("fields", event.changes().stream().map(AwardCorrected.Change::field).toList());
        return new NotificationDraft(event.userId(), NotificationType.AWARD_CORRECTED, payload, AWARD,
            event.awardId(), event.occurredAt());
    }

    /**
     * The reviewer's notification of overdue requests: how many and the oldest deadline.
     *
     * @param event the digest
     * @return one draft
     */
    public NotificationDraft of(OverdueNoticed event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("count", event.requests().size());
        payload.put("oldestDeadline", event.requests().stream().map(OverdueNoticed.Item::deadline)
            .min(Comparable::compareTo).map(Object::toString).orElse(null));
        return new NotificationDraft(event.userId(), NotificationType.REVIEW_OVERDUE, payload, null, null,
            event.occurredAt());
    }

    /**
     * The previous holder's notification of a takeover.
     *
     * @param event the takeover
     * @return one draft
     */
    public NotificationDraft of(ReviewTakenOver event) {
        return new NotificationDraft(event.userId(), NotificationType.REVIEW_TAKEN_OVER,
            award(event.title(), event.titleUk(), event.reviewer()), AWARD, event.awardId(), event.occurredAt());
    }

    /**
     * The holder's notification of a granted role.
     *
     * @param event the assignment
     * @return one draft
     */
    public NotificationDraft of(RoleAssigned event) {
        return new NotificationDraft(event.userId(), NotificationType.ROLE_ASSIGNED,
            authority(event.role(), event.organization(), event.organizationUk(), event.validTo()), null, null,
            event.occurredAt());
    }

    /**
     * The former holder's notification of a revoked role, with its last day.
     *
     * @param event the revocation
     * @return one draft
     */
    public NotificationDraft of(RoleRevoked event) {
        return new NotificationDraft(event.userId(), NotificationType.ROLE_REVOKED,
            authority(event.role(), event.organization(), event.organizationUk(), event.lastDay()), null, null,
            event.occurredAt());
    }

    /**
     * The delegate's notification of lent authority.
     *
     * @param event the delegation
     * @return one draft
     */
    public NotificationDraft of(DelegationCreated event) {
        Map<String, Object> payload = authority(event.role(), event.organization(), event.organizationUk(),
            event.validTo());
        payload.put(OTHER_PARTY, event.delegator());
        return new NotificationDraft(event.userId(), NotificationType.DELEGATION_CREATED, payload, null, null,
            event.occurredAt());
    }

    /**
     * One notification per party told of a revoked delegation, each naming the other side.
     *
     * @param event the revocation
     * @return one draft per recipient
     */
    public List<NotificationDraft> of(DelegationRevoked event) {
        return event.recipients().stream().map(recipient -> {
            Map<String, Object> payload = authority(event.role(), event.organization(), event.organizationUk(),
                event.validTo());
            payload.put(OTHER_PARTY, recipient.otherParty());
            return new NotificationDraft(recipient.userId(), NotificationType.DELEGATION_REVOKED, payload, null,
                null, event.occurredAt());
        }).toList();
    }

    /**
     * The notification type of a decision.
     *
     * @param event the decision
     * @return approved, rejected, returned or escalated
     */
    public static NotificationType typeOf(AwardDecided event) {
        return NotificationType.valueOf("AWARD_" + event.outcome().name());
    }

    private static Map<String, Object> award(String title, String titleUk, String reviewer) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("titleUk", titleUk);
        payload.put("reviewer", reviewer);
        return payload;
    }

    private static Map<String, Object> authority(RoleType role, String organization, String organizationUk,
                                                 LocalDate validTo) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("role", role.name());
        payload.put("organization", organization);
        payload.put("organizationUk", organizationUk);
        payload.put("validTo", validTo == null ? null : validTo.toString());
        return payload;
    }
}
