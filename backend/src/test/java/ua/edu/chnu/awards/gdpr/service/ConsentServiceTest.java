package ua.edu.chnu.awards.gdpr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.common.privacy.PrivacyNoticeAcknowledged;
import ua.edu.chnu.awards.common.privacy.PrivacyNoticeProperties;
import ua.edu.chnu.awards.common.privacy.PrivacyNoticeVersions;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.FieldViolation;
import ua.edu.chnu.awards.gdpr.dto.ConsentOverview;
import ua.edu.chnu.awards.gdpr.dto.ConsentOverview.HistoryEntry;
import ua.edu.chnu.awards.gdpr.dto.ConsentType;
import ua.edu.chnu.awards.gdpr.repository.ConsentRecords;

class ConsentServiceTest {

    private static final long USER_ID = 42L;
    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-01-10T09:00:00Z");

    private final ConsentRecords records = mock(ConsentRecords.class);
    private final AuditService audit = mock(AuditService.class);
    private ConsentService service;

    @BeforeEach
    void setUp() {
        PrivacyNoticeProperties properties = new PrivacyNoticeProperties("1.0", List.of(
            new PrivacyNoticeProperties.Version("0.9", LocalDate.parse("2026-01-01")),
            new PrivacyNoticeProperties.Version("1.0", LocalDate.parse("2026-10-15"))));
        service = new ConsentService(records, new PrivacyNoticeVersions(properties, event -> { }), audit,
            Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void ac15_noRecordsRequireAnAcknowledgementAndLeaveAnalyticsOff() {
        when(records.history(USER_ID)).thenReturn(List.of());

        ConsentOverview overview = service.overview(USER_ID);

        assertThat(overview.notice()).isEqualTo(new ConsentOverview.Notice("1.0", null, null, true));
        assertThat(overview.consents())
            .containsExactly(new ConsentOverview.Consent(ConsentType.ANALYTICS, false, null));
        assertThat(overview.history()).isEmpty();
    }

    @Test
    void ac15_theNewestRecordPerTypeIsTheCurrentState() {
        when(records.history(USER_ID)).thenReturn(List.of(
            entry(ConsentType.DATA_PROCESSING, "0.9", true, EARLIER),
            entry(ConsentType.ANALYTICS, "0.9", true, EARLIER.plusSeconds(60)),
            entry(ConsentType.DATA_PROCESSING, "1.0", true, NOW.minusSeconds(120)),
            entry(ConsentType.ANALYTICS, "1.0", false, NOW.minusSeconds(60))));

        ConsentOverview overview = service.overview(USER_ID);

        assertThat(overview.notice())
            .isEqualTo(new ConsentOverview.Notice("1.0", "1.0", NOW.minusSeconds(120), false));
        assertThat(overview.consents())
            .containsExactly(new ConsentOverview.Consent(ConsentType.ANALYTICS, false, NOW.minusSeconds(60)));
        assertThat(overview.history()).hasSize(4);
    }

    @Test
    void ac15_anOlderAcknowledgementRequiresANewOne() {
        when(records.history(USER_ID)).thenReturn(List.of(entry(ConsentType.DATA_PROCESSING, "0.9", true, EARLIER)));

        assertThat(service.overview(USER_ID).notice().acknowledgementRequired()).isTrue();
    }

    @Test
    void ac16_acknowledgingTheCurrentVersionAppendsAndAudits() {
        when(records.history(USER_ID)).thenReturn(List.of(entry(ConsentType.DATA_PROCESSING, "0.9", true, EARLIER)));

        ConsentOverview overview = service.acknowledge(USER_ID, "1.0");

        verify(records).lockOwner(USER_ID);
        verify(records).append(eq(USER_ID), eq(entry(ConsentType.DATA_PROCESSING, "1.0", true, NOW)), any(), any());
        verify(audit).record(AuditAction.PRIVACY_NOTICE_ACKNOWLEDGED, AuditEntityConstants.GDPR, USER_ID,
            Map.of("version", "1.0"));
        assertThat(overview.notice()).isEqualTo(new ConsentOverview.Notice("1.0", "1.0", NOW, false));
        assertThat(overview.history()).hasSize(2);
    }

    @Test
    void ac16_aRepeatedAcknowledgementAppendsNothing() {
        when(records.history(USER_ID)).thenReturn(List.of(entry(ConsentType.DATA_PROCESSING, "1.0", true, EARLIER)));

        ConsentOverview overview = service.acknowledge(USER_ID, "1.0");

        verify(records, never()).append(anyLong(), any(), any(), any());
        verifyNoInteractions(audit);
        assertThat(overview.notice().acknowledgedAt()).isEqualTo(EARLIER);
    }

    @Test
    void ac16_anOlderVersionIsOutdated() {
        assertThatThrownBy(() -> service.acknowledge(USER_ID, "0.9"))
            .isInstanceOfSatisfying(ApiProblemException.class, problem -> assertThat(violations(problem))
                .containsExactly(new FieldViolation("version", "outdated",
                    "The current privacy notice version is 1.0")));
        verifyNoInteractions(records, audit);
    }

    @Test
    void ac17_grantingAnalyticsAppendsAGrantAndAudits() {
        when(records.history(USER_ID)).thenReturn(List.of(entry(ConsentType.DATA_PROCESSING, "1.0", true, EARLIER)));

        ConsentOverview overview = service.choose(USER_ID, ConsentType.ANALYTICS, true);

        verify(records).lockOwner(USER_ID);
        verify(records).append(eq(USER_ID), eq(entry(ConsentType.ANALYTICS, "1.0", true, NOW)), any(), any());
        verify(audit).record(AuditAction.CONSENT_GRANTED, AuditEntityConstants.GDPR, USER_ID,
            Map.of("type", "ANALYTICS", "version", "1.0"));
        assertThat(overview.consents())
            .containsExactly(new ConsentOverview.Consent(ConsentType.ANALYTICS, true, NOW));
    }

    @Test
    void ac17_withdrawingAnalyticsAppendsAWithdrawalAndAudits() {
        when(records.history(USER_ID)).thenReturn(List.of(entry(ConsentType.ANALYTICS, "1.0", true, EARLIER)));

        service.choose(USER_ID, ConsentType.ANALYTICS, false);

        verify(records).append(eq(USER_ID), eq(entry(ConsentType.ANALYTICS, "1.0", false, NOW)), any(), any());
        verify(audit).record(AuditAction.CONSENT_WITHDRAWN, AuditEntityConstants.GDPR, USER_ID,
            Map.of("type", "ANALYTICS", "version", "1.0"));
    }

    @Test
    void ac17_theUnchangedStateAppendsNothing() {
        when(records.history(USER_ID)).thenReturn(List.of());

        ConsentOverview overview = service.choose(USER_ID, ConsentType.ANALYTICS, false);

        verify(records, never()).append(anyLong(), any(), any(), any());
        verifyNoInteractions(audit);
        assertThat(overview.consents())
            .containsExactly(new ConsentOverview.Consent(ConsentType.ANALYTICS, false, null));
    }

    @Test
    void ac17_dataProcessingIsNotOptional() {
        assertThatThrownBy(() -> service.choose(USER_ID, ConsentType.DATA_PROCESSING, false))
            .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                assertThat(violations(problem)).extracting(FieldViolation::field, FieldViolation::code)
                    .containsExactly(tuple("type", "not-optional"));
            });
        verifyNoInteractions(records, audit);
    }

    @Test
    void ac13_aRegistrationRecordsTheAcknowledgement() {
        when(records.history(USER_ID)).thenReturn(List.of());

        service.onPrivacyNoticeAcknowledged(new PrivacyNoticeAcknowledged(USER_ID, "1.0"));

        verify(records).append(eq(USER_ID), eq(entry(ConsentType.DATA_PROCESSING, "1.0", true, NOW)), any(), any());
        verify(audit).record(AuditAction.PRIVACY_NOTICE_ACKNOWLEDGED, AuditEntityConstants.GDPR, USER_ID,
            Map.of("version", "1.0"));
    }

    private static HistoryEntry entry(ConsentType type, String version, boolean granted, Instant at) {
        return new HistoryEntry(type, version, granted, at);
    }

    @SuppressWarnings("unchecked")
    private static List<FieldViolation> violations(ApiProblemException problem) {
        return (List<FieldViolation>) problem.getProperties().get("errors");
    }
}
