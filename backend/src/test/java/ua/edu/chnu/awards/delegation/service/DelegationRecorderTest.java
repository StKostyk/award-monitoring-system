package ua.edu.chnu.awards.delegation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditLog;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.delegation.entity.RoleDelegation;
import ua.edu.chnu.awards.delegation.event.DelegationCreated;
import ua.edu.chnu.awards.delegation.event.DelegationRevoked;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;

class DelegationRecorderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 24);

    private final AuditService audit = mock(AuditService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final DelegationRecorder recorder = new DelegationRecorder(audit, events);

    private final Organization faculty = Organization.builder().id(9L).orgType(OrganizationType.FACULTY)
        .name("Faculty of Mathematics and Informatics").nameUk("Факультет математики та інформатики").build();
    private final User dean = person(1L, "dean.fmi@chnu.edu.ua", "Марія");
    private final User secretary = person(2L, "secretary.fmi@chnu.edu.ua", "Аліна");

    @Test
    void ac3_5_adelegationIsAuditedWithItsFactsAndAnnouncedToTheDelegate() {
        recorder.created(delegation());

        ArgumentCaptor<Map<String, Object>> details = captor();
        verify(audit).record(eq(AuditAction.DELEGATION_CREATED), eq(AuditLog.AUTHORIZATION),
            eq(secretary.getId()), details.capture());
        assertThat(details.getValue()).containsEntry("actorId", dean.getId())
            .containsEntry("delegatorId", dean.getId())
            .containsEntry("delegateId", secretary.getId())
            .containsEntry("role", "DEAN")
            .containsEntry("organizationId", faculty.getId())
            .containsEntry("validFrom", TODAY.toString())
            .containsEntry("validTo", TODAY.plusDays(14).toString());
        DelegationCreated event = published(DelegationCreated.class);
        assertThat(event.email()).isEqualTo(secretary.getEmailAddress());
        assertThat(event.delegator()).isEqualTo("Марія Мартинюк");
        assertThat(event.organizationUk()).isEqualTo("Факультет математики та інформатики");
        assertThat(event.reason()).isEqualTo("Відпустка");
    }

    @Test
    void ac3_5_thedelegatorIsToldOnlyWhenSomebodyElseTookTheAuthorityBack() {
        recorder.revoked(dean, delegation());

        assertThat(published(DelegationRevoked.class).recipients())
            .containsExactly(secretary.getEmailAddress());
    }

    @Test
    void ac3_5_arevocationBySomebodyElseReachesBothSides() {
        recorder.revoked(person(3L, "admin@chnu.edu.ua", "Олег"), delegation());

        verify(audit).record(eq(AuditAction.DELEGATION_REVOKED), eq(AuditLog.AUTHORIZATION),
            eq(secretary.getId()), captor().capture());
        DelegationRevoked event = published(DelegationRevoked.class);
        assertThat(event.recipients())
            .containsExactly(secretary.getEmailAddress(), dean.getEmailAddress());
        assertThat(event.actor()).isEqualTo("Олег Мартинюк");
        assertThat(event.delegate()).isEqualTo("Аліна Мартинюк");
    }

    @Test
    void anOrganisationWithoutAUkrainianNameFallsBackToTheEnglishOne() {
        faculty.setNameUk(null);

        recorder.created(delegation());

        assertThat(published(DelegationCreated.class).organizationUk())
            .isEqualTo("Faculty of Mathematics and Informatics");
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

    private RoleDelegation delegation() {
        return RoleDelegation.builder().id(5L).delegator(dean).delegate(secretary).roleType(RoleType.DEAN)
            .organization(faculty).validFrom(TODAY).validTo(TODAY.plusDays(14)).reason("Відпустка").build();
    }

    private static User person(long id, String email, String firstName) {
        return User.builder().id(id).emailAddress(email).firstName(firstName).lastName("Мартинюк")
            .accountStatus(AccountStatus.ACTIVE).build();
    }
}
