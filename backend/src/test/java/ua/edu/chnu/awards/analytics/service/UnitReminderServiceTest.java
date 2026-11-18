package ua.edu.chnu.awards.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static ua.edu.chnu.awards.analytics.service.UnitReminderFixture.department;
import static ua.edu.chnu.awards.analytics.service.UnitReminderFixture.node;
import static ua.edu.chnu.awards.analytics.service.UnitReminderFixture.user;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

import ua.edu.chnu.awards.analytics.config.ReminderProperties;
import ua.edu.chnu.awards.analytics.dto.UnitReminder;
import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.authz.RoleScope;
import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.award.dto.UnitAwardCount;
import ua.edu.chnu.awards.award.service.UnitStatistics;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.notification.event.UnitReminderRequested;
import ua.edu.chnu.awards.organization.OrganizationTree;
import ua.edu.chnu.awards.organization.entity.OrganizationType;
import ua.edu.chnu.awards.organization.service.OrganizationLookup;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.service.UserAccounts;

@ExtendWith(MockitoExtension.class)
class UnitReminderServiceTest {

    private static final long FMI = 9L;
    private static final long DAI = 64L;
    private static final long OTHER_FACULTY = 10L;
    private static final long CALLER = 7L;
    private static final long COLLEAGUE = 8L;
    private static final int YEAR = 2026;
    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
    private static final Duration INTERVAL = Duration.ofDays(7);

    @Mock
    private AccessScope access;

    @Mock
    private OrganizationTree tree;

    @Mock
    private OrganizationLookup organizations;

    @Mock
    private UserAccounts users;

    @Mock
    private UnitStatistics statistics;

    @Mock
    private UnitReminderLog log;

    @Mock
    private AuditService audit;

    @Mock
    private ApplicationEventPublisher events;

    private UnitReminderService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneId.of("Europe/Kyiv"));
        service = new UnitReminderService(new UnitReminderRules(access, tree, users, statistics, clock), log, access,
            organizations, users, audit, events, clock, new ReminderProperties(INTERVAL));
        lenient().when(tree.node(FMI)).thenReturn(node(FMI, OrganizationType.FACULTY, null));
        lenient().when(tree.node(DAI)).thenReturn(node(DAI, OrganizationType.DEPARTMENT, FMI));
        lenient().when(tree.node(OTHER_FACULTY)).thenReturn(node(OTHER_FACULTY, OrganizationType.FACULTY, null));
        lenient().when(tree.covers(anyLong(), anyLong())).thenAnswer(call -> call.getArgument(0).equals(
            call.getArgument(1)) || call.<Long>getArgument(0) == FMI && call.<Long>getArgument(1) == DAI);
        lenient().when(tree.subtree(DAI)).thenReturn(Set.of(DAI));
        lenient().when(organizations.findById(DAI)).thenReturn(Optional.of(department(DAI)));
        lenient().when(access.callerId()).thenReturn(CALLER);
        lenient().when(users.findById(CALLER)).thenReturn(Optional.of(user(CALLER, "Аліна", FMI,
            AccountStatus.ACTIVE)));
        lenient().when(users.findById(COLLEAGUE)).thenReturn(Optional.of(user(COLLEAGUE, "Петро", DAI,
            AccountStatus.ACTIVE)));
        lenient().when(log.lastSent(anyCollection(), any())).thenReturn(Map.of());
        lenient().when(statistics.counts(anyCollection(), any())).thenReturn(List.of());
    }

    @Test
    void ac5_3_aSecretaryRemindsADepartmentWithoutAwardsThisYear() {
        holds(RoleType.FACULTY_SECRETARY, FMI);

        UnitReminder reminder = service.remind(DAI, COLLEAGUE);

        assertThat(reminder.unit().id()).isEqualTo(DAI);
        assertThat(reminder.recipient().id()).isEqualTo(COLLEAGUE);
        assertThat(reminder.sentAt()).isEqualTo(NOW);
        verify(log).lock(DAI);
        verify(audit).record(eq(AuditAction.UNIT_REMINDED), eq("organizations"), eq(CALLER),
            eq(DAI), eq(Map.of("recipientId", COLLEAGUE, "year", YEAR)));
        verify(events).publishEvent(new UnitReminderRequested(COLLEAGUE, "colleague8@chnu.edu.ua", "Петро", DAI,
            "Algebra and Informatics", "Кафедра алгебри та інформатики", YEAR, "Аліна Коваль", NOW));
    }

    @Test
    void ac5_3_aDeanWithABorrowedRoleMayRemindToo() {
        when(access.scopes()).thenReturn(List.of(new RoleScope(RoleType.EMPLOYEE, DAI),
            new RoleScope(RoleType.DEAN, FMI)));

        assertThat(service.remind(DAI, COLLEAGUE).unit().id()).isEqualTo(DAI);
    }

    @Test
    void ac5_4_aSecondReminderWithinTheIntervalIsRefusedWithTheDateOfTheLastOne() {
        holds(RoleType.DEAN, FMI);
        Instant last = NOW.minus(Duration.ofDays(2));
        when(log.lastSent(List.of(DAI), NOW.minus(INTERVAL))).thenReturn(Map.of(DAI, last));

        assertThatThrownBy(() -> service.remind(DAI, COLLEAGUE)).isInstanceOfSatisfying(ApiProblemException.class,
            problem -> {
                assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(problem.getType()).isEqualTo("recently-reminded");
                assertThat(problem.getProperties()).containsEntry("lastSentAt", last.toString());
            });
        verifyNothingSent();
    }

    @Test
    void ac5_5_aDepartmentOutsideTheCallersScopeIsForbidden() {
        holds(RoleType.FACULTY_SECRETARY, OTHER_FACULTY);

        assertThatThrownBy(() -> service.remind(DAI, COLLEAGUE)).isInstanceOf(AccessDeniedException.class);
        verifyNothingSent();
    }

    @Test
    void ac5_6_theRectorAndTheRectorsSecretaryCannotRemind() {
        when(access.scopes()).thenReturn(List.of(new RoleScope(RoleType.RECTOR_SECRETARY, 1L),
            new RoleScope(RoleType.RECTOR, 1L)));

        assertThatThrownBy(() -> service.remind(DAI, COLLEAGUE)).isInstanceOf(AccessDeniedException.class);
        verifyNothingSent();
    }

    @Test
    void ac5_5_aRecipientWhoIsNotAnActiveMemberOfTheDepartmentIsInvalid() {
        holds(RoleType.FACULTY_SECRETARY, FMI);
        when(users.findById(20L)).thenReturn(Optional.of(user(20L, "Ірина", FMI, AccountStatus.ACTIVE)));
        when(users.findById(21L)).thenReturn(Optional.of(user(21L, "Олег", DAI, AccountStatus.SUSPENDED)));
        when(users.findById(22L)).thenReturn(Optional.empty());

        for (long recipient : new long[] {20L, 21L, 22L}) {
            assertThatThrownBy(() -> service.remind(DAI, recipient)).as(String.valueOf(recipient))
                .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                    assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(problem.getType()).isEqualTo("invalid-recipient");
                });
        }
        verifyNothingSent();
    }

    @Test
    void ac5_5_aDepartmentWithAnAwardThisYearOrAFacultyHasAwards() {
        holds(RoleType.DEAN, FMI);
        when(statistics.counts(eq(Set.of(DAI)), any()))
            .thenReturn(List.of(new UnitAwardCount(DAI, YEAR, null, false, 1, 0)));

        for (long unit : new long[] {DAI, FMI}) {
            assertThatThrownBy(() -> service.remind(unit, COLLEAGUE)).as(String.valueOf(unit))
                .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                    assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(problem.getType()).isEqualTo("has-awards");
                });
        }
        verifyNothingSent();
    }

    @Test
    void ac5_5_anUnknownUnitIsNotFoundAndAMissingRecipientNamesTheParameter() {
        when(tree.node(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remind(999L, COLLEAGUE)).isInstanceOf(UnitNotFoundException.class);
        assertThatThrownBy(() -> service.remind(DAI, null)).isInstanceOfSatisfying(ApiProblemException.class,
            problem -> {
                assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(problem.getProperties()).containsEntry("parameter", "recipientId");
            });
        verifyNoInteractions(audit, events);
    }

    private void verifyNothingSent() {
        verify(audit, never()).record(any(), any(), any(), any(Long.class), any());
        verifyNoInteractions(events);
    }

    private void holds(RoleType role, long organizationId) {
        when(access.scopes()).thenReturn(List.of(new RoleScope(role, organizationId)));
    }
}
