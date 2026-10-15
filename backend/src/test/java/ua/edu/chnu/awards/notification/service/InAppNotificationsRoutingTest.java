package ua.edu.chnu.awards.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import ua.edu.chnu.awards.award.event.AwardCorrected;
import ua.edu.chnu.awards.award.event.OverdueNoticed;
import ua.edu.chnu.awards.award.event.ReviewTakenOver;
import ua.edu.chnu.awards.delegation.event.DelegationCreated;
import ua.edu.chnu.awards.delegation.event.DelegationRevoked;
import ua.edu.chnu.awards.notification.entity.NotificationType;
import ua.edu.chnu.awards.notification.repository.NewNotification;
import ua.edu.chnu.awards.notification.repository.NotificationRepository;
import ua.edu.chnu.awards.support.TestDeliveries;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.event.RoleAssigned;
import ua.edu.chnu.awards.user.event.RoleRevoked;
import ua.edu.chnu.awards.user.service.UserAccounts;

class InAppNotificationsRoutingTest {

    private static final Instant AT = Instant.parse("2026-10-14T09:59:00Z");

    private final NotificationDrafts drafts = mock(NotificationDrafts.class);
    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final UserAccounts users = mock(UserAccounts.class);
    private final NotificationPreferenceService preferences = mock(NotificationPreferenceService.class);
    private final InAppNotifications notifications = new InAppNotifications(drafts,
        TestDeliveries.rows(new ObjectMapper(), AT),
        repository, users, preferences, new DeliveryMetrics(new SimpleMeterRegistry()));

    @BeforeEach
    void allowEveryChannel() {
        when(preferences.allows(any(), any(), any())).thenReturn(true);
    }

    @Test
    void ac1_2_everyListedEventReachesTheWriter() {
        AwardCorrected corrected = mock(AwardCorrected.class);
        OverdueNoticed overdue = mock(OverdueNoticed.class);
        ReviewTakenOver takenOver = mock(ReviewTakenOver.class);
        RoleAssigned assigned = mock(RoleAssigned.class);
        RoleRevoked revoked = mock(RoleRevoked.class);
        DelegationCreated delegated = mock(DelegationCreated.class);
        DelegationRevoked withdrawn = mock(DelegationRevoked.class);
        when(users.findById(7L)).thenReturn(Optional.of(TestUsers.person(7L, "olena@chnu.edu.ua")));
        when(drafts.of(corrected)).thenReturn(draft(NotificationType.AWARD_CORRECTED));
        when(drafts.of(overdue)).thenReturn(draft(NotificationType.REVIEW_OVERDUE));
        when(drafts.of(takenOver)).thenReturn(draft(NotificationType.REVIEW_TAKEN_OVER));
        when(drafts.of(assigned)).thenReturn(draft(NotificationType.ROLE_ASSIGNED));
        when(drafts.of(revoked)).thenReturn(draft(NotificationType.ROLE_REVOKED));
        when(drafts.of(delegated)).thenReturn(draft(NotificationType.DELEGATION_CREATED));
        when(drafts.of(withdrawn)).thenReturn(List.of(draft(NotificationType.DELEGATION_REVOKED),
            draft(NotificationType.DELEGATION_REVOKED)));

        notifications.onAwardCorrected(corrected);
        notifications.onOverdueNoticed(overdue);
        notifications.onReviewTakenOver(takenOver);
        notifications.onRoleAssigned(assigned);
        notifications.onRoleRevoked(revoked);
        notifications.onDelegationCreated(delegated);
        notifications.onDelegationRevoked(withdrawn);

        ArgumentCaptor<NewNotification> rows = ArgumentCaptor.forClass(NewNotification.class);
        verify(repository, times(8)).insertOnce(rows.capture());
        assertThat(rows.getAllValues()).extracting(NewNotification::type).containsExactly("AWARD_CORRECTED",
            "REVIEW_OVERDUE", "REVIEW_TAKEN_OVER", "ROLE_ASSIGNED", "ROLE_REVOKED", "DELEGATION_CREATED",
            "DELEGATION_REVOKED", "DELEGATION_REVOKED");
    }

    private static NotificationDraft draft(NotificationType type) {
        return new NotificationDraft(7L, type, Map.of(), null, null, AT);
    }
}
