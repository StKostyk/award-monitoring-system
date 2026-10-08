package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
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
import ua.edu.chnu.awards.award.dto.WithdrawRequest;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.mapper.AwardMapper;
import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.award.repository.AwardRequestRepository;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.support.TestAwards;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.support.TestWorkflow;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.User;

class AwardWithdrawalTest {

    private final AwardRequestRepository requests = mock(AwardRequestRepository.class);
    private final AwardRepository awards = mock(AwardRepository.class);
    private final AwardOwnership ownership = mock(AwardOwnership.class);
    private final AuditService audit = mock(AuditService.class);
    private final Clock clock = Clock.fixed(TestAwards.SUBMITTED_AT, ZoneId.of("Europe/Kyiv"));
    private final AwardWithdrawal withdrawal = new AwardWithdrawal(requests, awards, ownership,
        new AwardMapper(TestWorkflow.estimator(clock)), audit);
    private final Organization department = TestUsers.organization(64L, OrganizationType.DEPARTMENT);
    private final User owner = TestUsers.person(21L, "owner@chnu.edu.ua", department);
    private Award award;
    private AwardRequest request;

    @BeforeEach
    void setUp() {
        award = TestAwards.award(owner, department).status(AwardStatus.PENDING).build();
        request = TestAwards.request(award).deadline(TestAwards.SUBMITTED_AT.plusSeconds(86_400)).build();
        when(requests.findByAwardIdForUpdate(5L)).thenReturn(Optional.of(request));
        when(awards.findForUpdate(5L)).thenReturn(Optional.of(award));
        when(ownership.isOwn(award)).thenReturn(true);
    }

    @ParameterizedTest
    @EnumSource(value = RequestStatus.class, names = {"SUBMITTED", "ESCALATED"})
    void ac3_1_anUnclaimedRequestIsWithdrawnAndTheAwardIsADraftAgain(RequestStatus status) {
        request.setStatus(status);
        request.setCurrentLevel(ApprovalLevel.DEAN);

        AwardResponse response = withdrawal.withdraw(5L, new WithdrawRequest(TestAwards.VERSION));

        assertThat(award.getStatus()).isEqualTo(AwardStatus.DRAFT);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.WITHDRAWN);
        assertThat(request.getDeadline()).isNull();
        assertThat(response.status()).isEqualTo(AwardStatus.DRAFT);
        assertThat(response.request().status()).isEqualTo(RequestStatus.WITHDRAWN);
        verify(requests).saveAndFlush(request);
        verify(awards).saveAndFlush(award);
        verify(audit).record(AuditAction.AWARD_WITHDRAWN, AuditEntityConstants.AWARDS, 21L, 5L,
            Map.of("requestId", 40L, "from", status.name(), "level", "DEAN"));
    }

    @Test
    void ac3_2_aClaimedRequestIsNotWithdrawn() {
        request.setStatus(RequestStatus.IN_REVIEW);
        request.setCurrentReviewer(TestUsers.person(31L, "secretary@chnu.edu.ua", department));

        assertProblem("request-claimed");
    }

    @ParameterizedTest
    @EnumSource(value = RequestStatus.class, names = {"APPROVED", "REJECTED"})
    void ac3_2_aFinalRequestIsNotPending(RequestStatus status) {
        request.setStatus(status);
        award.setStatus(status == RequestStatus.APPROVED ? AwardStatus.APPROVED : AwardStatus.REJECTED);

        assertProblem("award-not-pending");
    }

    @Test
    void ac3_2_aReturnedDraftIsNotPending() {
        request.setStatus(RequestStatus.RETURNED);
        award.setStatus(AwardStatus.DRAFT);

        assertProblem("award-not-pending");
    }

    @Test
    void ac3_2_aDraftNeverSubmittedIsNotPending() {
        award.setStatus(AwardStatus.DRAFT);
        when(requests.findByAwardIdForUpdate(5L)).thenReturn(Optional.empty());

        assertProblem("award-not-pending");
    }

    @Test
    void ac3_2_someoneElsesAwardIsNotFound() {
        when(ownership.isOwn(award)).thenReturn(false);

        assertThatThrownBy(() -> withdrawal.withdraw(5L, new WithdrawRequest(TestAwards.VERSION)))
            .isInstanceOf(AwardNotFoundException.class);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.SUBMITTED);
    }

    @Test
    void ac3_2_aStaleVersionChangesNothing() {
        doThrow(new ApiProblemException(HttpStatus.CONFLICT, "award-stale", "changed"))
            .when(ownership).requireVersion(award, 3L);

        assertThatThrownBy(() -> withdrawal.withdraw(5L, new WithdrawRequest(3L)))
            .isInstanceOf(ApiProblemException.class);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.SUBMITTED);
        assertThat(award.getStatus()).isEqualTo(AwardStatus.PENDING);
    }

    @Test
    void ac3_2_theVersionIsCheckedEvenWithoutABody() {
        withdrawal.withdraw(5L, null);

        verify(ownership).requireVersion(award, null);
    }

    private void assertProblem(String type) {
        RequestStatus before = request.getStatus();
        assertThatThrownBy(() -> withdrawal.withdraw(5L, new WithdrawRequest(TestAwards.VERSION)))
            .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(e.getType()).isEqualTo(type);
            });
        assertThat(request.getStatus()).isEqualTo(before);
        verify(requests, never()).saveAndFlush(any());
        verify(audit, never()).record(any(), anyString(), anyLong(), anyLong(), anyMap());
    }
}
