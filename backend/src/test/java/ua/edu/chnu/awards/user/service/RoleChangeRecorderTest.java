package ua.edu.chnu.awards.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditLog;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.entity.UserRole;
import ua.edu.chnu.awards.user.event.RoleAssigned;
import ua.edu.chnu.awards.user.event.RoleRevoked;

class RoleChangeRecorderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 22);

    private final AuditService audit = mock(AuditService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final RoleChangeRecorder recorder = new RoleChangeRecorder(audit, events);

    private final Organization faculty = Organization.builder().id(9L).orgType(OrganizationType.FACULTY)
        .name("Faculty of Mathematics and Informatics").nameUk("Факультет математики та інформатики").build();
    private final User actor = person(1L, "dean.fmi@chnu.edu.ua");
    private final User holder = person(2L, "employee.fmi@chnu.edu.ua");

    @Test
    void ac2_5_aGrantIsAuditedWithItsFactsAndAnnouncedToTheHolder() {
        recorder.granted(actor, assignment(TODAY, TODAY.plusMonths(1)));

        ArgumentCaptor<Map<String, Object>> details = captor();
        verify(audit).record(eq(AuditAction.ROLE_ASSIGNED), eq(AuditLog.AUTHORIZATION), eq(holder.getId()),
            details.capture());
        assertThat(details.getValue()).containsEntry("actorId", actor.getId())
            .containsEntry("role", "FACULTY_SECRETARY")
            .containsEntry("organizationId", faculty.getId())
            .containsEntry("validFrom", TODAY.toString())
            .containsEntry("validTo", TODAY.plusMonths(1).toString());
        RoleAssigned event = published(RoleAssigned.class);
        assertThat(event.email()).isEqualTo(holder.getEmailAddress());
        assertThat(event.organizationUk()).isEqualTo("Факультет математики та інформатики");
        assertThat(event.actor()).isEqualTo("Марія Мартинюк");
        assertThat(event.validTo()).isEqualTo(TODAY.plusMonths(1));
    }

    @Test
    void ac2_5_aRevocationIsAuditedAndAnnouncedWithItsLastDay() {
        recorder.revoked(actor, assignment(TODAY.minusMonths(2), TODAY.minusDays(1)));

        verify(audit).record(eq(AuditAction.ROLE_REVOKED), eq(AuditLog.AUTHORIZATION), eq(holder.getId()), any());
        assertThat(published(RoleRevoked.class).lastDay()).isEqualTo(TODAY.minusDays(1));
    }

    @Test
    void ac2_7_aRoleSupersededByACorrectedDepartmentIsAuditedWithoutAMessage() {
        recorder.superseded(actor, assignment(TODAY.minusYears(1), TODAY.minusDays(1)));

        verify(audit).record(eq(AuditAction.ROLE_REVOKED), eq(AuditLog.AUTHORIZATION), eq(holder.getId()), any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void anOrganisationWithoutAUkrainianNameFallsBackToTheEnglishOne() {
        faculty.setNameUk(null);

        recorder.granted(actor, assignment(TODAY, null));

        assertThat(published(RoleAssigned.class).organizationUk())
            .isEqualTo("Faculty of Mathematics and Informatics");
    }

    @Test
    void anOpenEndedAssignmentIsAuditedAsHavingNoEnd() {
        recorder.granted(actor, assignment(TODAY, null));

        ArgumentCaptor<Map<String, Object>> details = captor();
        verify(audit).record(any(), any(), anyLong(), details.capture());
        assertThat(details.getValue()).containsEntry("validTo", "null");
    }

    private <T> T published(Class<T> type) {
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(event.capture());
        return type.cast(event.getValue());
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Map<String, Object>> captor() {
        return ArgumentCaptor.forClass(Map.class);
    }

    private UserRole assignment(LocalDate from, LocalDate to) {
        return UserRole.builder().user(holder).roleType(RoleType.FACULTY_SECRETARY).organization(faculty)
            .validFrom(from).validTo(to).build();
    }

    private static User person(long id, String email) {
        return User.builder().id(id).emailAddress(email).firstName("Марія").lastName("Мартинюк")
            .accountStatus(AccountStatus.ACTIVE).build();
    }
}
