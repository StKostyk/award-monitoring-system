package ua.edu.chnu.awards.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.FieldViolation;
import ua.edu.chnu.awards.notification.dto.NotificationPreferenceItem;
import ua.edu.chnu.awards.notification.entity.NotificationChannel;
import ua.edu.chnu.awards.notification.entity.NotificationPreference;
import ua.edu.chnu.awards.notification.entity.NotificationType;
import ua.edu.chnu.awards.notification.repository.NotificationPreferenceRepository;

class NotificationPreferenceServiceTest {

    private static final long USER = 7L;

    private final NotificationPreferenceRepository repository = mock(NotificationPreferenceRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final NotificationPreferenceService service = new NotificationPreferenceService(repository, audit);

    @Test
    void ac3_1_withoutStoredRowsEveryTypeIsOnForBothChannels() {
        when(repository.findByUserId(USER)).thenReturn(List.of());

        List<NotificationPreferenceItem> items = service.preferences(USER);

        assertThat(items).extracting(NotificationPreferenceItem::type)
            .containsExactlyElementsOf(Arrays.stream(NotificationType.values()).map(Enum::name).toList());
        assertThat(items).allMatch(item -> item.email() && item.inApp());
    }

    @Test
    void ac3_2_storedRowsOverrideTheDefaults() {
        when(repository.findByUserId(USER)).thenReturn(List.of(row(NotificationType.ROLE_ASSIGNED, false, true)));

        assertThat(service.preferences(USER)).contains(item("ROLE_ASSIGNED", false, true),
            item("AWARD_APPROVED", true, true));
    }

    @Test
    void ac3_2_onlyChangedTypesAreWrittenAndTheFullListComesBack() {
        when(repository.findByUserId(USER)).thenReturn(List.of(row(NotificationType.ROLE_ASSIGNED, false, true)));

        List<NotificationPreferenceItem> items = service.update(USER, List.of(item("AWARD_APPROVED", false, true),
            item("ROLE_ASSIGNED", false, true), item("REVIEW_OVERDUE", true, true)));

        verify(repository).upsert(USER, "AWARD_APPROVED", false, true);
        verify(repository, never()).upsert(eq(USER), eq("ROLE_ASSIGNED"), anyBoolean(), anyBoolean());
        verify(repository, never()).upsert(eq(USER), eq("REVIEW_OVERDUE"), anyBoolean(), anyBoolean());
        assertThat(items).hasSize(NotificationType.values().length)
            .contains(item("AWARD_APPROVED", false, true), item("ROLE_ASSIGNED", false, true),
                item("AWARD_REJECTED", true, true));
    }

    @Test
    void ac3_3_bothChannelsOffIsRefused() {
        assertRefused(List.of(item("AWARD_APPROVED", false, false)),
            new FieldViolation("preferences[0]", "both-off", "Each type keeps at least one channel"));
    }

    @Test
    void ac3_3_anUnknownTypeIsRefused() {
        assertRefused(List.of(item("AWARD_APPROVED", true, true), item("AWARD_SUBMITTED", true, false)),
            new FieldViolation("preferences[1].type", "invalid", "Unknown notification type"));
    }

    @Test
    void ac3_3_aRepeatedTypeIsRefused() {
        assertRefused(List.of(item("ROLE_REVOKED", true, false), item("ROLE_REVOKED", false, true)),
            new FieldViolation("preferences[1].type", "duplicate", "The type is listed twice"));
    }

    @Test
    void ac3_3_aMissingChannelOrListIsRefused() {
        assertRefused(List.of(new NotificationPreferenceItem("ROLE_REVOKED", null, true)),
            new FieldViolation("preferences[0]", "required", "Both channels are required"));
        assertRefused(null, new FieldViolation("preferences", "required", "The list is required"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ac3_4_anEffectiveChangeIsAuditedOnceWithOldAndNewValues() {
        when(repository.findByUserId(USER)).thenReturn(List.of(row(NotificationType.ROLE_ASSIGNED, false, true)));

        service.update(USER, List.of(item("AWARD_APPROVED", true, false), item("ROLE_ASSIGNED", true, true),
            item("REVIEW_OVERDUE", true, true)));

        ArgumentCaptor<Map<String, Object>> before = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Object>> after = ArgumentCaptor.forClass(Map.class);
        verify(audit).recordChange(eq(AuditAction.NOTIFICATION_PREFERENCES_CHANGED), eq(AuditEntityConstants.USER),
            eq(USER), eq(USER), before.capture(), after.capture());
        assertThat(before.getValue()).containsOnly(
            Map.entry("AWARD_APPROVED", Map.of("email", true, "inApp", true)),
            Map.entry("ROLE_ASSIGNED", Map.of("email", false, "inApp", true)));
        assertThat(after.getValue()).containsOnly(
            Map.entry("AWARD_APPROVED", Map.of("email", true, "inApp", false)),
            Map.entry("ROLE_ASSIGNED", Map.of("email", true, "inApp", true)));
    }

    @Test
    void ac3_4_aRequestThatChangesNothingWritesNoAuditRow() {
        when(repository.findByUserId(USER)).thenReturn(List.of());

        service.update(USER, List.of(item("AWARD_APPROVED", true, true)));

        verify(repository, never()).upsert(anyLong(), anyString(), anyBoolean(), anyBoolean());
        verifyNoInteractions(audit);
    }

    @Test
    void ac3_5_aChannelIsAllowedUnlessItsStoredPreferenceIsOff() {
        when(repository.findByUserIdAndType(USER, NotificationType.AWARD_APPROVED))
            .thenReturn(Optional.of(row(NotificationType.AWARD_APPROVED, false, true)));
        when(repository.findByUserIdAndType(USER, NotificationType.ROLE_ASSIGNED)).thenReturn(Optional.empty());

        assertThat(service.allows(USER, NotificationType.AWARD_APPROVED, NotificationChannel.EMAIL)).isFalse();
        assertThat(service.allows(USER, NotificationType.AWARD_APPROVED, NotificationChannel.IN_APP)).isTrue();
        assertThat(service.allows(USER, NotificationType.ROLE_ASSIGNED, NotificationChannel.EMAIL)).isTrue();
        assertThat(service.allows(null, NotificationType.ROLE_ASSIGNED, NotificationChannel.EMAIL)).isTrue();
    }

    private void assertRefused(List<NotificationPreferenceItem> items, FieldViolation violation) {
        assertThatThrownBy(() -> service.update(USER, items))
            .isInstanceOfSatisfying(ApiProblemException.class, problem ->
                assertThat(problem.getProperties()).containsEntry("errors", List.of(violation)));
        verify(repository, never()).upsert(anyLong(), anyString(), anyBoolean(), anyBoolean());
        verify(audit, never()).recordChange(any(), any(), any(), any(), any(), any());
    }

    private static NotificationPreferenceItem item(String type, boolean email, boolean inApp) {
        return new NotificationPreferenceItem(type, email, inApp);
    }

    private static NotificationPreference row(NotificationType type, boolean email, boolean inApp) {
        return new NotificationPreference(null, USER, type, email, inApp);
    }
}
