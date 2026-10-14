package ua.edu.chnu.awards.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ua.edu.chnu.awards.award.event.AwardCorrected;
import ua.edu.chnu.awards.award.event.AwardDecided;
import ua.edu.chnu.awards.award.event.OverdueNoticed;
import ua.edu.chnu.awards.award.event.ReviewTakenOver;
import ua.edu.chnu.awards.delegation.event.DelegationCreated;
import ua.edu.chnu.awards.delegation.event.DelegationRevoked;
import ua.edu.chnu.awards.notification.entity.NotificationChannel;
import ua.edu.chnu.awards.notification.repository.NewNotification;
import ua.edu.chnu.awards.notification.repository.NotificationRepository;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.event.RoleAssigned;
import ua.edu.chnu.awards.user.event.RoleRevoked;
import ua.edu.chnu.awards.user.service.UserAccounts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes the in-app notifications of committed events, in a transaction of their own on the publication registry:
 * a failed insert is retried, and a repeated delivery hits the dedupe key and writes nothing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InAppNotifications {

    private final NotificationDrafts drafts;
    private final NotificationKeys keys;
    private final NotificationRepository notifications;
    private final UserAccounts users;
    private final ObjectMapper mapper;
    private final Clock clock;

    /**
     * Tells the owner about a decision.
     *
     * @param event the committed decision
     */
    @ApplicationModuleListener
    public void onAwardDecided(AwardDecided event) {
        write(drafts.of(event));
    }

    /**
     * Tells the owner about a correction.
     *
     * @param event the committed correction
     */
    @ApplicationModuleListener
    public void onAwardCorrected(AwardCorrected event) {
        write(drafts.of(event));
    }

    /**
     * Tells a reviewer about overdue requests.
     *
     * @param event the committed digest
     */
    @ApplicationModuleListener
    public void onOverdueNoticed(OverdueNoticed event) {
        write(drafts.of(event));
    }

    /**
     * Tells the previous holder about a takeover.
     *
     * @param event the committed takeover
     */
    @ApplicationModuleListener
    public void onReviewTakenOver(ReviewTakenOver event) {
        write(drafts.of(event));
    }

    /**
     * Tells the holder about a granted role.
     *
     * @param event the committed assignment
     */
    @ApplicationModuleListener
    public void onRoleAssigned(RoleAssigned event) {
        write(drafts.of(event));
    }

    /**
     * Tells the former holder about a revoked role.
     *
     * @param event the committed revocation
     */
    @ApplicationModuleListener
    public void onRoleRevoked(RoleRevoked event) {
        write(drafts.of(event));
    }

    /**
     * Tells the delegate about lent authority.
     *
     * @param event the committed delegation
     */
    @ApplicationModuleListener
    public void onDelegationCreated(DelegationCreated event) {
        write(drafts.of(event));
    }

    /**
     * Tells each party about a revoked delegation.
     *
     * @param event the committed revocation
     */
    @ApplicationModuleListener
    public void onDelegationRevoked(DelegationRevoked event) {
        drafts.of(event).forEach(this::write);
    }

    void write(NotificationDraft draft) {
        if (draft.userId() == null || draft.occurredAt() == null || !reachable(draft.userId())) {
            log.info("No in-app notification {} for user {}: no recipient", draft.type(), draft.userId());
            return;
        }
        Instant sentAt = clock.instant();
        Instant expiresAt = sentAt.atOffset(ZoneOffset.UTC).plusYears(1).toInstant();
        int written = notifications.insertOnce(new NewNotification(draft.userId(), draft.type().name(),
            NotificationChannel.IN_APP.name(), json(draft), keys.key(draft, NotificationChannel.IN_APP),
            draft.referenceType(), draft.referenceId(), sentAt, expiresAt));
        if (written == 0) {
            log.info("In-app notification {} for user {} was written before", draft.type(), draft.userId());
        }
    }

    private boolean reachable(long userId) {
        return users.findById(userId)
            .filter(user -> user.getAccountStatus() != AccountStatus.DELETED)
            .map(User::getEmailAddress)
            .filter(address -> !address.isBlank())
            .isPresent();
    }

    private String json(NotificationDraft draft) {
        try {
            return mapper.writeValueAsString(draft.payload());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Payload of " + draft.type() + " cannot be written", e);
        }
    }
}
