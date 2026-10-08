package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.dto.ReviewItem;
import ua.edu.chnu.awards.award.dto.ReviewerCandidate;
import ua.edu.chnu.awards.award.dto.ReviewerChange;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.mapper.ReviewItemMapper;
import ua.edu.chnu.awards.award.repository.AwardRequestRepository;
import ua.edu.chnu.awards.award.repository.ReviewDecisionRepository;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

class ReviewAssignmentTest {

    private static final long AWARD = 1L;
    private static final long REQUEST = 2L;
    private static final long VERSION = 4L;
    private static final long FACULTY = 9L;

    private final AwardRequestRepository requests = mock(AwardRequestRepository.class);
    private final ReviewDecisionRepository decisions = mock(ReviewDecisionRepository.class);
    private final ReviewerRule rule = mock(ReviewerRule.class);
    private final ReviewerAvailability availability = mock(ReviewerAvailability.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final ReviewItemMapper mapper = mock(ReviewItemMapper.class);
    private final AccessScope access = mock(AccessScope.class);
    private final ReviewAssignment assignment = new ReviewAssignment(requests, decisions, rule, availability, users,
        audit, mapper, access, new ReviewGuards(requests, rule, access));

    private final Organization faculty = TestUsers.organization(FACULTY, OrganizationType.FACULTY);
    private final User owner = TestUsers.person(7L, "employee@chnu.edu.ua", faculty);
    private final User caller = TestUsers.person(5L, "secretary@chnu.edu.ua", faculty);
    private final User peer = TestUsers.person(6L, "secretary2@chnu.edu.ua", faculty);
    private AwardRequest request;

    @BeforeEach
    void setUp() {
        Award award = Award.builder().id(AWARD).owner(owner).organization(faculty).build();
        request = AwardRequest.builder().id(REQUEST).award(award).submitter(owner).status(RequestStatus.SUBMITTED)
            .currentLevel(ApprovalLevel.FACULTY_SECRETARY).version(VERSION).build();
        when(requests.findByAwardIdForUpdate(AWARD)).thenReturn(Optional.of(request));
        when(requests.findByAwardId(AWARD)).thenReturn(Optional.of(request));
        when(access.callerId()).thenReturn(caller.getId());
        when(users.findById(caller.getId())).thenReturn(Optional.of(caller));
        when(users.findById(peer.getId())).thenReturn(Optional.of(peer));
        own(ApprovalLevel.FACULTY_SECRETARY);
    }

    @Test
    void ac1_4_aClaimMakesTheCallerTheReviewerAndIsAudited() {
        assignment.assign(AWARD, new ReviewerChange(VERSION, null, null));

        assertThat(request.getCurrentReviewer()).isEqualTo(caller);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.IN_REVIEW);
        verify(requests).saveAndFlush(request);
        assertThat(audited(AuditAction.REVIEW_CLAIMED)).containsEntry("requestId", REQUEST)
            .containsEntry("level", "FACULTY_SECRETARY").doesNotContainKey("delegatorId");
    }

    @Test
    void ac1_4_aDelegateClaimNamesTheDelegator() {
        when(rule.grant(request)).thenReturn(Optional.of(new ReviewGrant(ApprovalLevel.FACULTY_SECRETARY, FACULTY,
            8L)));

        assignment.assign(AWARD, new ReviewerChange(VERSION, null, null));

        assertThat(audited(AuditAction.REVIEW_CLAIMED)).containsEntry("delegatorId", 8L);
    }

    @Test
    void ac1_4_claimingARequestOneAlreadyHoldsChangesNothing() {
        held(caller);

        assignment.assign(AWARD, new ReviewerChange(VERSION - 1, null, null));

        verify(requests, never()).saveAndFlush(any());
        verify(mapper).toItem(request);
    }

    @Test
    void ac1_5_aRequestClaimedByAColleagueAnswersRequestClaimed() {
        held(peer);

        assertProblem(() -> assignment.assign(AWARD, new ReviewerChange(VERSION, null, null)),
            HttpStatus.CONFLICT, "request-claimed");
    }

    @Test
    void ac1_5_aStaleVersionAnswersRequestStale() {
        assertProblem(() -> assignment.assign(AWARD, new ReviewerChange(VERSION - 1, null, null)),
            HttpStatus.CONFLICT, "request-stale");
    }

    @Test
    void ac1_4_aMissingVersionIsAValidationFailure() {
        assertProblem(() -> assignment.assign(AWARD, new ReviewerChange(null, null, null)),
            HttpStatus.UNPROCESSABLE_ENTITY, "validation-failed");
    }

    @Test
    void ac1_6_aHigherLevelTakesARequestOverFromAPeer() {
        held(peer);
        eligible(peer, ApprovalLevel.FACULTY_SECRETARY);
        when(rule.highestLevel(request)).thenReturn(Optional.of(ApprovalLevel.DEAN));

        assignment.assign(AWARD, new ReviewerChange(VERSION, null, true));

        assertThat(request.getCurrentReviewer()).isEqualTo(caller);
        assertThat(audited(AuditAction.REVIEW_TAKEN_OVER)).containsEntry("previousReviewerId", peer.getId());
    }

    @Test
    void ac1_6_aTakeOverNeedsALevelAboveTheHolderNotOnlyAboveTheRequest() {
        held(peer);
        eligible(peer, ApprovalLevel.FACULTY_SECRETARY);
        eligible(peer, ApprovalLevel.DEAN);
        when(rule.highestLevel(request)).thenReturn(Optional.of(ApprovalLevel.DEAN));

        assertProblem(() -> assignment.assign(AWARD, new ReviewerChange(VERSION, null, true)),
            HttpStatus.CONFLICT, "request-claimed");
    }

    @Test
    void ac1_6_aPeerCannotTakeOverWhileTheReviewerIsStillEligible() {
        held(peer);
        when(availability.isEligible(peer.getId(), ApprovalLevel.FACULTY_SECRETARY, FACULTY, owner.getId(),
            owner.getId())).thenReturn(true);

        assertProblem(() -> assignment.assign(AWARD, new ReviewerChange(VERSION, null, true)),
            HttpStatus.CONFLICT, "request-claimed");
    }

    @Test
    void ac1_6_aPeerTakesOverFromAReviewerWhoIsNoLongerEligible() {
        held(peer);

        assignment.assign(AWARD, new ReviewerChange(VERSION, null, true));

        assertThat(request.getCurrentReviewer()).isEqualTo(caller);
    }

    @Test
    void ac1_6_aHigherLevelWithoutTakeOverStillGetsRequestClaimed() {
        held(peer);
        when(rule.highestLevel(request)).thenReturn(Optional.of(ApprovalLevel.DEAN));

        assertProblem(() -> assignment.assign(AWARD, new ReviewerChange(VERSION, null, false)),
            HttpStatus.CONFLICT, "request-claimed");
    }

    @Test
    void ac1_7_releaseReturnsTheRequestToSubmitted() {
        held(caller);

        assignment.release(AWARD, VERSION);

        assertThat(request.getCurrentReviewer()).isNull();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.SUBMITTED);
        assertThat(audited(AuditAction.REVIEW_RELEASED)).containsEntry("requestId", REQUEST);
    }

    @Test
    void ac1_7_releaseAfterADecisionBelowReturnsTheRequestToEscalated() {
        request.setCurrentLevel(ApprovalLevel.DEAN);
        own(ApprovalLevel.DEAN);
        held(caller);
        when(decisions.existsByRequestIdAndLevelIn(eq(REQUEST), anyCollection())).thenReturn(true);

        assignment.release(AWARD, VERSION);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.ESCALATED);
    }

    @Test
    void ac1_7_onlyTheReviewerReleases() {
        held(peer);

        assertProblem(() -> assignment.release(AWARD, VERSION), HttpStatus.CONFLICT, "request-claimed");
    }

    @Test
    void ac1_7_aRequestNobodyHoldsIsNeitherReleasedNorHandedOver() {
        held(peer);
        request.setCurrentReviewer(null);
        request.setStatus(RequestStatus.SUBMITTED);

        assertProblem(() -> assignment.release(AWARD, VERSION), HttpStatus.CONFLICT, "request-claimed");
        assertProblem(() -> assignment.assign(AWARD, new ReviewerChange(VERSION, peer.getId(), null)),
            HttpStatus.CONFLICT, "request-claimed");
    }

    @Test
    void ac1_8_theCandidatesAreTheEligiblePeersOtherThanTheCaller() {
        held(caller);
        when(availability.candidates(ApprovalLevel.FACULTY_SECRETARY, FACULTY, owner.getId(), owner.getId()))
            .thenReturn(List.of(candidate(caller, false),
                candidate(peer, true)));

        assertThat(assignment.candidates(AWARD)).extracting(ReviewerCandidate::id, ReviewerCandidate::delegated)
            .containsExactly(tuple(peer.getId(), true));
    }

    @Test
    void ac1_8_aHandOverGivesTheRequestToAnEligiblePeer() {
        held(caller);
        when(availability.candidates(ApprovalLevel.FACULTY_SECRETARY, FACULTY, owner.getId(), owner.getId()))
            .thenReturn(List.of(candidate(peer, false)));

        assignment.assign(AWARD, new ReviewerChange(VERSION, peer.getId(), null));

        assertThat(request.getCurrentReviewer()).isEqualTo(peer);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.IN_REVIEW);
        assertThat(audited(AuditAction.REVIEW_HANDED_OVER)).containsEntry("previousReviewerId", caller.getId())
            .containsEntry("reviewerId", peer.getId());
    }

    @Test
    void ac1_8_aHandOverToSomebodyNotEligibleIsRefused() {
        held(caller);
        when(availability.candidates(any(), anyLong(), anyLong(), anyLong())).thenReturn(List.of());

        assertProblem(() -> assignment.assign(AWARD, new ReviewerChange(VERSION, 99L, null)),
            HttpStatus.UNPROCESSABLE_ENTITY, "reviewer-not-eligible");
    }

    @Test
    void ac1_9_aRequestTheCallerMayNotReviewIsReportedAsMissing() {
        when(rule.grant(request)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignment.assign(AWARD, new ReviewerChange(VERSION, null, null)))
            .isInstanceOf(AwardNotFoundException.class);
        assertThatThrownBy(() -> assignment.candidates(AWARD)).isInstanceOf(AwardNotFoundException.class);
    }

    @Test
    void ac1_9_aReturnedOrUnknownRequestIsReportedAsMissing() {
        request.setStatus(RequestStatus.RETURNED);
        when(requests.findByAwardIdForUpdate(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignment.release(AWARD, VERSION)).isInstanceOf(AwardNotFoundException.class);
        assertThatThrownBy(() -> assignment.release(3L, VERSION)).isInstanceOf(AwardNotFoundException.class);
    }

    @Test
    void ac1_11_theItemOfAnOpenRequestTheCallerMayReview() {
        ReviewItem item = mock(ReviewItem.class);
        when(mapper.toItem(request)).thenReturn(item);

        assertThat(assignment.item(AWARD)).isSameAs(item);
    }

    @Test
    void ac1_11_noItemForADecidedOrForeignRequest() {
        request.setStatus(RequestStatus.APPROVED);
        assertThatThrownBy(() -> assignment.item(AWARD)).isInstanceOf(AwardNotFoundException.class);

        request.setStatus(RequestStatus.SUBMITTED);
        when(rule.grant(request)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> assignment.item(AWARD)).isInstanceOf(AwardNotFoundException.class);
        assertThatThrownBy(() -> assignment.item(3L)).isInstanceOf(AwardNotFoundException.class);
    }

    @Test
    void ac1_9_aDecidedRequestAnswersRequestClosed() {
        request.setStatus(RequestStatus.APPROVED);

        assertProblem(() -> assignment.assign(AWARD, new ReviewerChange(VERSION, null, null)),
            HttpStatus.CONFLICT, "request-closed");
    }

    private static ReviewerAvailability.Candidate candidate(User user, boolean delegated) {
        return new ReviewerAvailability.Candidate(user.getId(), user.getFullName(), user.getEmailAddress(),
            delegated);
    }

    private void own(ApprovalLevel level) {
        when(rule.grant(request)).thenReturn(Optional.of(new ReviewGrant(level, FACULTY, null)));
        when(rule.highestLevel(request)).thenReturn(Optional.of(level));
    }

    private void eligible(User reviewer, ApprovalLevel level) {
        when(availability.isEligible(reviewer.getId(), level, FACULTY, owner.getId(), owner.getId())).thenReturn(true);
    }

    private void held(User reviewer) {
        request.setCurrentReviewer(reviewer);
        request.setStatus(RequestStatus.IN_REVIEW);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> audited(AuditAction action) {
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(audit).record(eq(action), anyString(), eq(caller.getId()), eq(AWARD), details.capture());
        return details.getValue();
    }

    private void assertProblem(Runnable call, HttpStatus status, String type) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiProblemException.class, problem -> {
            assertThat(problem.getStatus()).isEqualTo(status);
            assertThat(problem.getType()).endsWith(type);
        });
        verify(audit, never()).record(any(), anyString(), anyLong(), anyLong(), anyMap());
    }
}
