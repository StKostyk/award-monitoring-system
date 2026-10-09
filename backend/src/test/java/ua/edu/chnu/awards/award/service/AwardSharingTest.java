package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.award.dto.AwardResponse;
import ua.edu.chnu.awards.award.dto.AwardVisibilityUpdate;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.AwardVisibility;
import ua.edu.chnu.awards.award.mapper.AwardMapper;
import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.support.TestAwards;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.support.TestWorkflow;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.User;

class AwardSharingTest {

    private final AwardRepository awards = mock(AwardRepository.class);
    private final AwardOwnership ownership = mock(AwardOwnership.class);
    private final RequestLookup requests = mock(RequestLookup.class);
    private final AuditService audit = mock(AuditService.class);
    private final Clock clock = Clock.fixed(TestAwards.SUBMITTED_AT, ZoneId.of("Europe/Kyiv"));
    private final AwardSharing sharing = new AwardSharing(awards, ownership, requests,
        new AwardMapper(TestWorkflow.estimator(clock)), audit);
    private final Organization department = TestUsers.organization(64L, OrganizationType.DEPARTMENT);
    private final User owner = TestUsers.person(21L, "owner@chnu.edu.ua", department);
    private Award award;

    @BeforeEach
    void setUp() {
        award = TestAwards.award(owner, department).status(AwardStatus.APPROVED).build();
        when(awards.findForUpdate(TestAwards.AWARD_ID)).thenReturn(Optional.of(award));
        when(ownership.isOwn(award)).thenReturn(true);
        when(requests.of(TestAwards.AWARD_ID)).thenReturn(Optional.empty());
    }

    @Test
    void ac1_2_theOwnerSharesAnApprovedAwardAndTheChangeIsAudited() {
        AwardResponse response = sharing.update(TestAwards.AWARD_ID,
            new AwardVisibilityUpdate(AwardVisibility.UNIVERSITY));

        assertThat(award.getVisibility()).isEqualTo(AwardVisibility.UNIVERSITY);
        assertThat(response.visibility()).isEqualTo(AwardVisibility.UNIVERSITY);
        assertThat(response.version()).isEqualTo(TestAwards.VERSION);
        verify(awards).updateVisibility(TestAwards.AWARD_ID, "UNIVERSITY");
        verify(audit).record(AuditAction.AWARD_VISIBILITY_CHANGED, AuditEntityConstants.AWARDS, owner.getId(),
            TestAwards.AWARD_ID, Map.of("from", "PRIVATE", "to", "UNIVERSITY"));
    }

    @Test
    void ac1_2_theSameValueAgainChangesNothing() {
        award.setVisibility(AwardVisibility.PUBLIC);

        AwardResponse response = sharing.update(TestAwards.AWARD_ID,
            new AwardVisibilityUpdate(AwardVisibility.PUBLIC));

        assertThat(response.visibility()).isEqualTo(AwardVisibility.PUBLIC);
        verify(awards, never()).updateVisibility(anyLong(), any());
        verify(audit, never()).record(any(), any(), anyLong(), anyLong(), anyMap());
    }

    @Test
    void ac1_4_anAwardOfSomeoneElseIsNotFound() {
        when(ownership.isOwn(award)).thenReturn(false);

        assertThatThrownBy(() -> sharing.update(TestAwards.AWARD_ID,
            new AwardVisibilityUpdate(AwardVisibility.PUBLIC)))
            .isInstanceOf(AwardNotFoundException.class);
        assertThat(award.getVisibility()).isEqualTo(AwardVisibility.PRIVATE);
    }

    @ParameterizedTest
    @EnumSource(value = AwardStatus.class, names = "APPROVED", mode = EnumSource.Mode.EXCLUDE)
    void ac1_4_anAwardThatIsNotApprovedKeepsItsVisibility(AwardStatus status) {
        award.setStatus(status);

        assertThatThrownBy(() -> sharing.update(TestAwards.AWARD_ID,
            new AwardVisibilityUpdate(AwardVisibility.UNIVERSITY)))
            .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(problem.getType()).isEqualTo("visibility-fixed");
                assertThat(problem.getProperties()).containsEntry("awardStatus", status.name());
            });
        assertThat(award.getVisibility()).isEqualTo(AwardVisibility.PRIVATE);
    }

    @Test
    void ac1_4_aUnitAwardHasNoChoice() {
        award.setRecipientOrganizationId(department.getId());

        assertThatThrownBy(() -> sharing.update(TestAwards.AWARD_ID,
            new AwardVisibilityUpdate(AwardVisibility.PUBLIC)))
            .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                assertThat(problem.getType()).isEqualTo("visibility-fixed");
                assertThat(problem.getProperties()).doesNotContainKey("awardStatus");
            });
    }

    @Test
    void ac1_4_aMissingValueIsAFieldError() {
        assertThatThrownBy(() -> sharing.update(TestAwards.AWARD_ID, new AwardVisibilityUpdate(null)))
            .isInstanceOfSatisfying(ApiProblemException.class,
                problem -> assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        verify(awards, never()).findForUpdate(anyLong());
    }

    @Test
    void ac1_1_aUnitAwardShowsNoVisibility() {
        award.setRecipientOrganizationId(department.getId());
        when(ownership.isOwn(award)).thenReturn(false);

        assertThat(new AwardMapper(TestWorkflow.estimator(clock)).toResponse(award, null).visibility()).isNull();
    }
}
