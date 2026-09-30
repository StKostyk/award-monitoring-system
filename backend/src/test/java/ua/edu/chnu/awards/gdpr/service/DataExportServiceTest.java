package ua.edu.chnu.awards.gdpr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.auth.service.DeviceFingerprint;
import ua.edu.chnu.awards.auth.service.RequestThrottle;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.gdpr.dto.DataExport;
import ua.edu.chnu.awards.gdpr.dto.PersonalDataFile;
import ua.edu.chnu.awards.gdpr.event.DataExported;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;
import ua.edu.chnu.awards.user.service.UserNotFoundException;

class DataExportServiceTest {

    private static final Instant LATE_EVENING_UTC = Instant.parse("2026-09-30T22:30:00Z");
    private static final String FIREFOX = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:130.0) Gecko/20100101 "
        + "Firefox/130.0";

    private final UserRepository users = mock(UserRepository.class);
    private final PersonalDataAssembler assembler = mock(PersonalDataAssembler.class);
    private final RequestThrottle throttle = mock(RequestThrottle.class);
    private final AuditService audit = mock(AuditService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final DataExportService service = new DataExportService(users, assembler, throttle, audit,
        new DeviceFingerprint(), events, Clock.fixed(LATE_EVENING_UTC, ZoneId.of("Europe/Kyiv")));

    private final User user = TestUsers.person(5L, "employee.fmi@chnu.edu.ua", null);
    private final PersonalDataFile file = new PersonalDataFile(
        new PersonalDataFile.Metadata(LATE_EVENING_UTC, 5L, "1.0", "Article 20"), null, List.of(), List.of(),
        List.of(), List.of(), List.of(), List.of(), List.of());

    @BeforeEach
    void bindRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.7");
        request.addHeader("User-Agent", FIREFOX);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(users.findById(5L)).thenReturn(Optional.of(user));
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void ac31_ac34_theExportIsNamedAfterTheKyivDayAuditedAndAnnounced() {
        when(throttle.claimForTransaction("gdpr:export:5", DataExportService.INTERVAL)).thenReturn(true);
        when(assembler.assemble(user)).thenReturn(file);

        DataExport export = service.export(5L);

        assertThat(export.fileName()).isEqualTo("award-monitoring-export-2026-10-01.json");
        assertThat(export.content()).isSameAs(file);
        verify(audit).record(AuditAction.DATA_EXPORT, "GDPR", 5L, 5L, file.sectionCounts());
        ArgumentCaptor<DataExported> event = ArgumentCaptor.forClass(DataExported.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue()).isEqualTo(new DataExported("employee.fmi@chnu.edu.ua", "Марія",
            LATE_EVENING_UTC, "10.0.0.7", "Firefox"));
    }

    @Test
    void ac34_aSecondExportWithinAMinuteIsRefusedBeforeAnythingIsRead() {
        when(throttle.claimForTransaction(anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.export(5L))
            .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                assertThat(problem.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                assertThat(problem.getType()).isEqualTo("too-many-requests");
            });
        verifyNoInteractions(assembler, audit, events);
    }

    @Test
    void edge_anUnknownCallerIsNotFound() {
        when(users.findById(6L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.export(6L)).isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(throttle);
    }

    @Test
    void ac34_theSectionCountsAreWhatTheAuditKeeps() {
        assertThat(file.sectionCounts()).isEqualTo(Map.of("roles", 0, "delegations", 0, "awards", 0,
            "documents", 0, "consent_history", 0, "devices", 0, "activity_log", 0));
    }
}
