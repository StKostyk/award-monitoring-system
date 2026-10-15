package ua.edu.chnu.awards.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import ua.edu.chnu.awards.award.event.AwardDecided;
import ua.edu.chnu.awards.notification.entity.NotificationChannel;
import ua.edu.chnu.awards.notification.entity.NotificationType;
import ua.edu.chnu.awards.notification.repository.NewNotification;
import ua.edu.chnu.awards.notification.repository.NotificationRepository;
import ua.edu.chnu.awards.support.TestEvents;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.service.UserAccounts;

class InAppNotificationsTest {

    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");
    private static final Instant AT = Instant.parse("2026-10-14T09:59:00Z");
    private static final String EMAIL = "olena@chnu.edu.ua";

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final UserAccounts users = mock(UserAccounts.class);
    private final NotificationPreferenceService preferences = mock(NotificationPreferenceService.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final DeliveryMetrics metrics = new DeliveryMetrics(registry);
    private final InAppNotifications notifications = new InAppNotifications(new NotificationDrafts(),
        new NotificationRows(new NotificationKeys(), new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC)),
        repository, users, preferences, metrics);

    @BeforeEach
    void allowEveryChannel() {
        when(preferences.allows(any(), any(), any())).thenReturn(true);
    }

    @Test
    void ac1_2_aRowIsWrittenForTheRecipientWithAYearOfRetention() {
        when(users.findById(7L)).thenReturn(Optional.of(TestUsers.person(7L, EMAIL)));

        notifications.onAwardDecided(decided(7L, AT));

        ArgumentCaptor<NewNotification> row = ArgumentCaptor.forClass(NewNotification.class);
        verify(repository).insertOnce(row.capture());
        assertThat(row.getValue()).extracting(NewNotification::userId, NewNotification::type,
                NewNotification::channel, NewNotification::referenceType, NewNotification::referenceId,
                NewNotification::sentAt, NewNotification::expiresAt)
            .containsExactly(7L, "AWARD_APPROVED", "IN_APP", "AWARD", 5L, NOW,
                Instant.parse("2027-10-14T10:00:00Z"));
        assertThat(row.getValue().payload()).contains("\"title\":\"Certificate\"", "\"level\":\"DEAN\"");
        assertThat(row.getValue().dedupeKey()).hasSize(64);
    }

    @Test
    void ac3_5_noRowIfTheRecipientSwitchedInAppOffForTheType() {
        when(users.findById(7L)).thenReturn(Optional.of(TestUsers.person(7L, EMAIL)));
        when(preferences.allows(7L, NotificationType.AWARD_APPROVED, NotificationChannel.IN_APP)).thenReturn(false);

        notifications.onAwardDecided(decided(7L, AT));

        verify(repository, never()).insertOnce(any());
    }

    @Test
    void ac1_5_noRowForAMissingDeletedOrAddresslessRecipient() {
        User deleted = TestUsers.person(8L, "gone@chnu.edu.ua");
        deleted.setAccountStatus(AccountStatus.DELETED);
        when(users.findById(7L)).thenReturn(Optional.empty());
        when(users.findById(8L)).thenReturn(Optional.of(deleted));
        when(users.findById(9L)).thenReturn(Optional.of(TestUsers.person(9L, " ")));

        notifications.onAwardDecided(decided(7L, AT));
        notifications.onAwardDecided(decided(8L, AT));
        notifications.onAwardDecided(decided(9L, AT));

        verify(repository, never()).insertOnce(any());
    }

    @Test
    void edgeCase_anEventStoredBeforeTheRecipientWasCarriedWritesNothing() {
        notifications.onAwardDecided(decided(null, AT));
        notifications.onAwardDecided(decided(7L, null));

        verifyNoInteractions(repository, users);
    }

    @Test
    void ac1_3_aRepeatedDeliveryThatWritesNothingIsNoError() {
        when(users.findById(7L)).thenReturn(Optional.of(TestUsers.person(7L, EMAIL)));
        when(repository.insertOnce(any())).thenReturn(0);

        notifications.onAwardDecided(decided(7L, AT));

        verify(repository).insertOnce(any());
    }

    @Test
    void aPayloadThatCannotBeWrittenFailsThePublication() throws JsonProcessingException {
        ObjectMapper broken = mock(ObjectMapper.class);
        when(broken.writeValueAsString(any())).thenThrow(mock(JsonProcessingException.class));
        when(users.findById(7L)).thenReturn(Optional.of(TestUsers.person(7L, EMAIL)));
        InAppNotifications failing = new InAppNotifications(new NotificationDrafts(),
            new NotificationRows(new NotificationKeys(), broken, Clock.fixed(NOW, ZoneOffset.UTC)), repository, users,
            preferences, metrics);

        assertThatThrownBy(() -> failing.onAwardDecided(decided(7L, AT))).isInstanceOf(IllegalStateException.class);
    }

    private static AwardDecided decided(Long userId, Instant at) {
        return TestEvents.decided().to(userId, EMAIL, "Олена").at(at).build();
    }
}
