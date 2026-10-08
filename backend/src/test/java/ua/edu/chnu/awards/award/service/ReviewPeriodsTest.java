package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.authz.DelegatedScope;
import ua.edu.chnu.awards.authz.OrganizationTree;
import ua.edu.chnu.awards.authz.RoleScope;
import ua.edu.chnu.awards.award.dto.ReviewPeriodResponse;
import ua.edu.chnu.awards.award.dto.ReviewPeriodUpdate;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.support.TestWorkflow;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;

class ReviewPeriodsTest {

    private static final long CALLER = 5L;
    private static final long DELEGATOR = 8L;
    private static final long FACULTY = 9L;
    private static final long OTHER_FACULTY = 10L;
    private static final long DEPARTMENT = 64L;
    private static final int DEFAULT_DAYS = 3;
    private static final int FACULTY_DAYS = 5;

    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final OrganizationTree tree = mock(OrganizationTree.class);
    private final AccessScope access = mock(AccessScope.class);
    private final AuditService audit = mock(AuditService.class);
    private final StatusEstimator estimator = TestWorkflow.estimator(
        Clock.fixed(Instant.parse("2026-10-01T09:00:00Z"), ZoneId.of("Europe/Kyiv")), DEFAULT_DAYS);
    private final ReviewPeriods periods = new ReviewPeriods(organizations, tree, access, estimator, audit);
    private final Organization faculty = TestUsers.organization(FACULTY, OrganizationType.FACULTY);

    @BeforeEach
    void setUp() {
        when(organizations.findById(FACULTY)).thenReturn(Optional.of(faculty));
        when(organizations.findById(DEPARTMENT)).thenReturn(Optional.of(
            TestUsers.organization(DEPARTMENT, OrganizationType.DEPARTMENT)));
        when(access.callerId()).thenReturn(CALLER);
        when(access.scopes()).thenReturn(List.of());
        when(access.heldScopes()).thenReturn(List.of());
        when(access.delegations()).thenReturn(List.of());
        when(tree.covers(anyLong(), anyLong())).thenReturn(false);
        when(tree.covers(FACULTY, FACULTY)).thenReturn(true);
    }

    @Test
    void ac1_1_aFacultyWithoutItsOwnPeriodShowsTheDefault() {
        held(RoleType.DEAN, FACULTY);

        assertThat(periods.read(FACULTY))
            .isEqualTo(new ReviewPeriodResponse(FACULTY, null, DEFAULT_DAYS, DEFAULT_DAYS, true));
    }

    @Test
    void ac1_2_theDeanSetsThePeriodAndTheChangeIsAudited() {
        held(RoleType.DEAN, FACULTY);

        ReviewPeriodResponse response = periods.update(FACULTY, days("5"));

        assertThat(response).isEqualTo(new ReviewPeriodResponse(FACULTY, FACULTY_DAYS, FACULTY_DAYS, DEFAULT_DAYS,
            true));
        assertThat(faculty.getReviewWorkingDays()).isEqualTo(FACULTY_DAYS);
        verify(audit).recordChange(AuditAction.REVIEW_PERIOD_CHANGED, AuditEntityConstants.ORGANIZATIONS, CALLER,
            FACULTY, values(null, DEFAULT_DAYS), values(FACULTY_DAYS, FACULTY_DAYS));
    }

    @Test
    void ac1_2_aDelegatedDeanSetsThePeriodOnBehalfOfTheDelegator() {
        when(access.delegations()).thenReturn(List.of(new DelegatedScope(RoleType.DEAN, FACULTY, DELEGATOR)));

        periods.update(FACULTY, days("5"));

        Map<String, Object> after = values(FACULTY_DAYS, FACULTY_DAYS);
        after.put("delegatorId", DELEGATOR);
        verify(audit).recordChange(AuditAction.REVIEW_PERIOD_CHANGED, AuditEntityConstants.ORGANIZATIONS, CALLER,
            FACULTY, values(null, DEFAULT_DAYS), after);
    }

    @Test
    void ac1_3_theDefaultIsRestoredWithNull() {
        faculty.setReviewWorkingDays(FACULTY_DAYS);
        held(RoleType.DEAN, FACULTY);

        ReviewPeriodResponse response = periods.update(FACULTY, new ReviewPeriodUpdate(null));

        assertThat(response.workingDays()).isNull();
        assertThat(response.effectiveWorkingDays()).isEqualTo(DEFAULT_DAYS);
        verify(audit).recordChange(AuditAction.REVIEW_PERIOD_CHANGED, AuditEntityConstants.ORGANIZATIONS, CALLER,
            FACULTY, values(FACULTY_DAYS, FACULTY_DAYS), values(null, DEFAULT_DAYS));
    }

    @Test
    void edge_anUnchangedPeriodWritesNoAuditRow() {
        faculty.setReviewWorkingDays(FACULTY_DAYS);
        held(RoleType.DEAN, FACULTY);

        periods.update(FACULTY, days("5.0"));

        verifyNoInteractions(audit);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "21", "-1", "5.5", "100000000000"})
    void ac1_4_aPeriodOutsideOneToTwentyOrNotWholeIsRefused(String value) {
        held(RoleType.DEAN, FACULTY);

        assertThatThrownBy(() -> periods.update(FACULTY, days(value)))
            .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                assertThat(problem.toProblem().getProperties()).containsKey("errors");
            });
        assertThat(faculty.getReviewWorkingDays()).isNull();
    }

    @Test
    void ac1_4_theLimitsAreAccepted() {
        held(RoleType.DEAN, FACULTY);

        assertThat(periods.update(FACULTY, days("1")).workingDays()).isEqualTo(ReviewPeriods.MIN_WORKING_DAYS);
        assertThat(periods.update(FACULTY, days("20")).workingDays()).isEqualTo(ReviewPeriods.MAX_WORKING_DAYS);
    }

    @Test
    void ac1_5_aFacultySecretaryReadsButCannotChange() {
        when(access.scopes()).thenReturn(List.of(new RoleScope(RoleType.FACULTY_SECRETARY, FACULTY)));
        when(access.heldScopes()).thenReturn(List.of(new RoleScope(RoleType.FACULTY_SECRETARY, FACULTY)));

        assertThat(periods.read(FACULTY).updatable()).isFalse();
        assertThatThrownBy(() -> periods.update(FACULTY, days("5"))).isInstanceOf(FacultyNotFoundException.class);
        verifyNoInteractions(audit);
    }

    @Test
    void ac1_5_aDeanOfAnotherFacultyFindsNothing() {
        held(RoleType.DEAN, OTHER_FACULTY);

        assertThatThrownBy(() -> periods.read(FACULTY)).isInstanceOf(FacultyNotFoundException.class);
        assertThatThrownBy(() -> periods.update(FACULTY, days("5"))).isInstanceOf(FacultyNotFoundException.class);
    }

    @Test
    void ac1_5_aDepartmentOrAnUnknownOrganisationIsNoFaculty() {
        held(RoleType.DEAN, FACULTY);

        assertThatThrownBy(() -> periods.read(DEPARTMENT)).isInstanceOf(FacultyNotFoundException.class);
        assertThatThrownBy(() -> periods.update(1L, days("5"))).isInstanceOf(FacultyNotFoundException.class);
    }

    @Test
    void ac1_5_aSystemAdministratorReadsAndChangesEveryFaculty() {
        when(access.has("system:configure")).thenReturn(true);

        assertThat(periods.read(FACULTY).updatable()).isTrue();
        periods.update(FACULTY, days("4"));

        verify(audit).recordChange(any(), any(), any(), any(), any(), any());
    }

    private void held(RoleType role, long organizationId) {
        RoleScope scope = new RoleScope(role, organizationId);
        when(access.scopes()).thenReturn(List.of(scope));
        when(access.heldScopes()).thenReturn(List.of(scope));
        when(tree.covers(organizationId, organizationId)).thenReturn(true);
    }

    private static ReviewPeriodUpdate days(String value) {
        return new ReviewPeriodUpdate(new BigDecimal(value));
    }

    private static Map<String, Object> values(Integer own, int effective) {
        Map<String, Object> values = new HashMap<>();
        values.put("workingDays", own);
        values.put("effectiveWorkingDays", effective);
        return values;
    }
}
