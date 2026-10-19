package ua.edu.chnu.awards.gdpr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.auth.service.DeviceLookup;
import ua.edu.chnu.awards.award.service.AwardLookup;
import ua.edu.chnu.awards.award.service.AwardVersionLookup;
import ua.edu.chnu.awards.delegation.service.DelegationLookup;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile.NotificationEntry;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile.NotificationPreferenceEntry;
import ua.edu.chnu.awards.gdpr.mapper.PersonalDataMapper;
import ua.edu.chnu.awards.gdpr.repository.PersonalDataQueries;
import ua.edu.chnu.awards.notification.service.NotificationLookup;
import ua.edu.chnu.awards.notification.service.NotificationLookup.NotificationRecord;
import ua.edu.chnu.awards.notification.service.NotificationLookup.PreferenceRecord;
import ua.edu.chnu.awards.organization.entity.OrganizationType;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.service.RoleHolders;

class PersonalDataNotificationsTest {

    private static final Instant NOW = Instant.parse("2026-10-14T09:00:00Z");
    private static final Instant SENT = NOW.minusSeconds(60);
    private static final Map<String, Object> PAYLOAD = Map.of("title", "Certificate");

    private final NotificationLookup notifications = mock(NotificationLookup.class);
    private final PersonalDataAssembler assembler = new PersonalDataAssembler(mock(RoleHolders.class),
        mock(DelegationLookup.class), mock(AwardLookup.class), mock(AwardVersionLookup.class),
        mock(DeviceLookup.class), notifications, mock(PersonalDataQueries.class), new PersonalDataMapper(),
        Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void ac4_5_theFormatIsOnePointFourWithNotificationsOfBothChannelsAndTheStoredPreferences() {
        when(notifications.notificationsOf(5L)).thenReturn(List.of(
            new NotificationRecord("AWARD_RETURNED", "EMAIL", PAYLOAD, false, SENT, null, NOW, "SENT"),
            new NotificationRecord("AWARD_RETURNED", "IN_APP", PAYLOAD, true, SENT, NOW, null, null)));
        when(notifications.preferencesOf(5L)).thenReturn(List.of(new PreferenceRecord("AWARD_RETURNED", false, true)));

        PersonalDataFile file = assembler.assemble(person());

        assertThat(file.exportMetadata().formatVersion()).isEqualTo("1.7");
        assertThat(file.notifications()).containsExactly(
            new NotificationEntry("AWARD_RETURNED", "EMAIL", PAYLOAD, false, SENT, null, NOW, "SENT"),
            new NotificationEntry("AWARD_RETURNED", "IN_APP", PAYLOAD, true, SENT, NOW, null, null));
        assertThat(file.notificationPreferences())
            .containsExactly(new NotificationPreferenceEntry("AWARD_RETURNED", false, true));
        assertThat(file.sectionCounts()).containsEntry("notifications", 2)
            .containsEntry("notification_preferences", 1);
    }

    @Test
    void ac4_5_withoutNotificationsBothSectionsAreEmptyNotMissing() {
        PersonalDataFile file = assembler.assemble(person());

        assertThat(file.notifications()).isEmpty();
        assertThat(file.notificationPreferences()).isEmpty();
    }

    private static User person() {
        User user = TestUsers.person(5L, "employee.fmi@chnu.edu.ua", null);
        user.setOrganization(TestUsers.organization(1L, OrganizationType.UNIVERSITY));
        return user;
    }
}
