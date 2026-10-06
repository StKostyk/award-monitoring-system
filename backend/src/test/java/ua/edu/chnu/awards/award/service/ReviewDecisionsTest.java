package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.dto.ReviewDecisionRequest;
import ua.edu.chnu.awards.award.dto.ReviewDecisionRequest.Decision;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardCategory;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.entity.ReviewDecisionType;
import ua.edu.chnu.awards.award.repository.AwardRequestRepository;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.document.repository.DocumentRepository;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

class ReviewDecisionsTest {

    private static final long AWARD = 1L;
    private static final long VERSION = 4L;
    private static final long FACULTY = 9L;
    private static final long DELEGATOR = 8L;
    private static final Instant NOW = Instant.parse("2026-10-07T09:00:00Z");
    private static final Instant NEXT_DEADLINE = Instant.parse("2026-10-14T09:00:00Z");

    private final AwardRequestRepository requests = mock(AwardRequestRepository.class);
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ReviewerRule rule = mock(ReviewerRule.class);
    private final StartLevel startLevel = mock(StartLevel.class);
    private final StatusEstimator estimator = mock(StatusEstimator.class);
    private final AwardHistory history = mock(AwardHistory.class);
    private final DecisionLog log = mock(DecisionLog.class);
    private final AccessScope access = mock(AccessScope.class);
    private final ReviewDecisions service = new ReviewDecisions(requests, new DecisionRules(documents), users,
        new ReviewGuards(requests, rule, access), rule, new Transitions(), startLevel, estimator, history, log, access,
        Clock.fixed(NOW, ZoneId.of("Europe/Kyiv")));

    private final Organization faculty = TestUsers.organization(FACULTY, OrganizationType.FACULTY);
    private final User owner = TestUsers.person(7L, "employee@chnu.edu.ua", faculty);
    private final User caller = TestUsers.person(5L, "secretary@chnu.edu.ua", faculty);
    private final User peer = TestUsers.person(6L, "secretary2@chnu.edu.ua", faculty);
    private Award award;
    private AwardRequest request;

    @BeforeEach
    void setUp() {
        award = Award.builder().id(AWARD).owner(owner).organization(faculty).status(AwardStatus.PENDING)
            .category(AwardCategory.builder().level(RecognitionLevel.FACULTY).build()).build();
        request = AwardRequest.builder().id(2L).award(award).submitter(owner).status(RequestStatus.SUBMITTED)
            .currentLevel(ApprovalLevel.FACULTY_SECRETARY).version(VERSION).deadline(NOW).build();
        when(requests.findByAwardIdForUpdate(AWARD)).thenReturn(Optional.of(request));
        when(access.callerId()).thenReturn(caller.getId());
        when(users.findById(caller.getId())).thenReturn(Optional.of(caller));
        when(rule.grant(request)).thenReturn(Optional.of(new ReviewGrant(ApprovalLevel.FACULTY_SECRETARY, FACULTY,
            null)));
        when(estimator.deadline(NOW)).thenReturn(NEXT_DEADLINE);
        when(rule.delegatorId(request)).thenReturn(null);
    }

    @Test
    void ac2_1_anApprovalAtTheMinimumApprovesAnUnclaimedRequestInOneStep() {
        assertThat(service.decide(AWARD, decision(Decision.APPROVE, null)))
            .extracting("awardId", "status", "requestStatus", "level", "requestVersion")
            .containsExactly(AWARD, AwardStatus.APPROVED, RequestStatus.APPROVED, ApprovalLevel.FACULTY_SECRETARY,
                VERSION);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.APPROVED);
        assertThat(request.getCompletedAt()).isEqualTo(NOW);
        assertThat(request.getCurrentReviewer()).isEqualTo(caller);
        assertThat(award.getStatus()).isEqualTo(AwardStatus.APPROVED);
        verify(log).write(request, ApprovalLevel.FACULTY_SECRETARY, ReviewDecisionType.APPROVED, caller, null, null,
            NOW);
        verify(requests).saveAndFlush(request);
        verify(history).decided(award);
        verify(log).audit(request, Decision.APPROVE, ApprovalLevel.FACULTY_SECRETARY, caller.getId(), null);
        verify(log).announce(request, caller, null);
    }

    @Test
    void ac2_2_anApprovalBelowTheMinimumPassesTheRequestOn() {
        award.setCategory(AwardCategory.builder().level(RecognitionLevel.NATIONAL).build());
        held(caller);
        when(startLevel.from(ApprovalLevel.DEAN, FACULTY, owner.getId())).thenReturn(ApprovalLevel.DEAN);

        service.decide(AWARD, decision(Decision.APPROVE, "Підтверджено"));

        assertThat(request.getStatus()).isEqualTo(RequestStatus.ESCALATED);
        assertThat(request.getCurrentLevel()).isEqualTo(ApprovalLevel.DEAN);
        assertThat(request.getCurrentReviewer()).isNull();
        assertThat(request.getDeadline()).isEqualTo(NEXT_DEADLINE);
        assertThat(award.getStatus()).isEqualTo(AwardStatus.PENDING);
        verify(log).write(request, ApprovalLevel.FACULTY_SECRETARY, ReviewDecisionType.APPROVED, caller,
            "Підтверджено", null, NOW);
    }

    @Test
    void ac2_3_aRejectionKeepsTheCommentAsTheReason() {
        held(caller);

        service.decide(AWARD, decision(Decision.REJECT, "  Не відповідає положенню  "));

        assertThat(request.getStatus()).isEqualTo(RequestStatus.REJECTED);
        assertThat(request.getRejectionReason()).isEqualTo("Не відповідає положенню");
        assertThat(request.getCompletedAt()).isEqualTo(NOW);
        assertThat(award.getStatus()).isEqualTo(AwardStatus.REJECTED);
    }

    @Test
    void ac2_3_aRejectionOrReturnWithoutCommentIsRefused() {
        assertProblem(() -> service.decide(AWARD, decision(Decision.REJECT, " ")), HttpStatus.UNPROCESSABLE_ENTITY,
            "validation-failed");
        assertProblem(() -> service.decide(AWARD, decision(Decision.RETURN, null)), HttpStatus.UNPROCESSABLE_ENTITY,
            "validation-failed");
    }

    @Test
    void ac2_3_aCommentLongerThanTheLimitIsRefused() {
        String tooLong = "x".repeat(DecisionRules.MAX_COMMENT + 1);

        assertProblem(() -> service.decide(AWARD, decision(Decision.APPROVE, tooLong)),
            HttpStatus.UNPROCESSABLE_ENTITY, "validation-failed");
    }

    @Test
    void ac2_4_aReturnGivesTheDraftBackWithoutReviewerOrDeadline() {
        held(caller);

        service.decide(AWARD, decision(Decision.RETURN, "Додайте номер наказу"));

        assertThat(request.getStatus()).isEqualTo(RequestStatus.RETURNED);
        assertThat(request.getCurrentReviewer()).isNull();
        assertThat(request.getDeadline()).isNull();
        assertThat(request.getCompletedAt()).isNull();
        assertThat(award.getStatus()).isEqualTo(AwardStatus.DRAFT);
    }

    @Test
    void ac2_5_anEscalationMovesToTheNextLevelNotPassedOver() {
        request.setCurrentLevel(ApprovalLevel.DEAN);
        when(startLevel.from(ApprovalLevel.RECTOR_SECRETARY, FACULTY, owner.getId()))
            .thenReturn(ApprovalLevel.RECTOR);

        service.decide(AWARD, decision(Decision.ESCALATE, null));

        assertThat(request.getStatus()).isEqualTo(RequestStatus.ESCALATED);
        assertThat(request.getCurrentLevel()).isEqualTo(ApprovalLevel.RECTOR);
        assertThat(request.getDeadline()).isEqualTo(NEXT_DEADLINE);
        verify(log).write(request, ApprovalLevel.DEAN, ReviewDecisionType.ESCALATED, caller, null, null, NOW);
    }

    @Test
    void ac2_5_theRectorCannotEscalate() {
        request.setCurrentLevel(ApprovalLevel.RECTOR);

        assertProblem(() -> service.decide(AWARD, decision(Decision.ESCALATE, null)), HttpStatus.CONFLICT,
            "no-higher-level");
    }

    @Test
    void ac2_6_aVerifiedApprovalSetsTheBadge() {
        when(documents.countByAwardId(AWARD)).thenReturn(1L);

        service.decide(AWARD, new ReviewDecisionRequest(Decision.APPROVE, VERSION, null, true));

        assertThat(award.isVerificationBadge()).isTrue();
    }

    @Test
    void ac2_6_aVerifiedApprovalWithoutDocumentsIsRefused() {
        assertProblem(() -> service.decide(AWARD, new ReviewDecisionRequest(Decision.APPROVE, VERSION, null, true)),
            HttpStatus.UNPROCESSABLE_ENTITY, "validation-failed");
        assertThat(award.isVerificationBadge()).isFalse();
    }

    @Test
    void ac2_7_aDelegateDecisionNamesTheDelegator() {
        when(rule.delegatorId(request)).thenReturn(DELEGATOR);

        service.decide(AWARD, decision(Decision.APPROVE, null));

        verify(log).write(request, ApprovalLevel.FACULTY_SECRETARY, ReviewDecisionType.APPROVED, caller, null,
            DELEGATOR, NOW);
        verify(log).audit(request, Decision.APPROVE, ApprovalLevel.FACULTY_SECRETARY, caller.getId(), DELEGATOR);
    }

    @Test
    void ac2_8_aRequestHeldByAColleagueAnswersRequestClaimed() {
        held(peer);

        assertProblem(() -> service.decide(AWARD, decision(Decision.APPROVE, null)), HttpStatus.CONFLICT,
            "request-claimed");
    }

    @Test
    void ac2_8_aStaleVersionAnswersRequestStale() {
        assertProblem(() -> service.decide(AWARD, new ReviewDecisionRequest(Decision.APPROVE, VERSION - 1, null,
            null)), HttpStatus.CONFLICT, "request-stale");
    }

    @Test
    void ac2_8_aDecidedRequestAnswersRequestClosed() {
        request.setStatus(RequestStatus.APPROVED);

        assertProblem(() -> service.decide(AWARD, decision(Decision.REJECT, "Ні")), HttpStatus.CONFLICT,
            "request-closed");
    }

    @Test
    void ac2_8_aRequestTheCallerMayNotReviewIsNotFound() {
        when(rule.grant(request)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.decide(AWARD, decision(Decision.APPROVE, null)))
            .isInstanceOf(AwardNotFoundException.class);
        verifyNoInteractions(log);
    }

    @Test
    void ac2_8_aMissingDecisionOrVersionIsAValidationFailure() {
        assertProblem(() -> service.decide(AWARD, new ReviewDecisionRequest(null, null, null, null)),
            HttpStatus.UNPROCESSABLE_ENTITY, "validation-failed");
    }

    private void held(User reviewer) {
        request.setCurrentReviewer(reviewer);
        request.setStatus(RequestStatus.IN_REVIEW);
    }

    private static ReviewDecisionRequest decision(Decision decision, String comment) {
        return new ReviewDecisionRequest(decision, VERSION, comment, null);
    }

    private void assertProblem(Runnable call, HttpStatus status, String type) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiProblemException.class, problem -> {
            assertThat(problem.getStatus()).isEqualTo(status);
            assertThat(problem.getType()).isEqualTo(type);
        });
        verify(log, never()).write(any(), any(), any(), any(), any(), any(), any());
        verify(requests, never()).saveAndFlush(any());
    }
}
