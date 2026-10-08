package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.award.dto.AwardResponse;
import ua.edu.chnu.awards.award.dto.SubmitRequest;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardCategory;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.mapper.AwardMapper;
import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.award.repository.AwardRequestRepository;
import ua.edu.chnu.awards.support.TestAwards;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.support.TestWorkflow;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.User;

class AwardResubmissionTest {

    private static final Instant NOW = Instant.parse("2026-09-28T09:00:00Z");
    private static final Instant THREE_WORKING_DAYS_LATER = Instant.parse("2026-10-01T09:00:00Z");

    private final AwardRequestRepository requests = mock(AwardRequestRepository.class);
    private final AwardOwnership ownership = mock(AwardOwnership.class);
    private final AuditService audit = mock(AuditService.class);
    private final DuplicateFinder duplicates = mock(DuplicateFinder.class);
    private final StartLevel startLevel = mock(StartLevel.class);
    private final Clock clock = Clock.fixed(NOW, ZoneId.of("Europe/Kyiv"));
    private final StatusEstimator estimator = TestWorkflow.estimator(clock);
    private final AwardSubmission submission = new AwardSubmission(mock(AwardRepository.class), requests, ownership,
        mock(AwardInputRules.class), duplicates, new AwardMapper(estimator), audit, mock(AwardHistory.class),
        estimator, startLevel, clock);
    private final Organization department = TestUsers.organization(69L, OrganizationType.DEPARTMENT);
    private final User owner = TestUsers.person(21L, "owner@chnu.edu.ua", department);
    private Award draft;

    @BeforeEach
    void setUp() {
        AwardCategory category = AwardCategory.builder().id(13L).name("Ministry")
            .level(RecognitionLevel.NATIONAL).active(true).build();
        draft = TestAwards.award(owner, department).category(category).build();
        when(ownership.lockedDraft(5L)).thenReturn(draft);
        when(startLevel.of(69L, 21L)).thenReturn(ApprovalLevel.FACULTY_SECRETARY);
        when(requests.saveAndFlush(any(AwardRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void ac3_3_aWithdrawnAwardReusesItsRequestFromTheStartLevel() {
        AwardRequest earlier = earlier(RequestStatus.WITHDRAWN, ApprovalLevel.DEAN);

        AwardResponse response = submission.submit(5L, new SubmitRequest(4L, null));

        assertThat(response.request().status()).isEqualTo(RequestStatus.SUBMITTED);
        assertThat(response.request().currentLevel()).isEqualTo(ApprovalLevel.FACULTY_SECRETARY);
        assertThat(response.request().submittedAt()).isEqualTo(NOW);
        assertThat(response.request().deadline()).isEqualTo(THREE_WORKING_DAYS_LATER);
        verify(requests).saveAndFlush(earlier);
        verify(audit).record(AuditAction.AWARD_SUBMITTED, AuditEntityConstants.AWARDS, 21L, 5L,
            Map.of("requestId", 40L, "level", "FACULTY_SECRETARY", "organizationId", 69L,
                "resubmittedFrom", "WITHDRAWN"));
    }

    @Test
    void ac3_4_aReturnedAwardGoesBackToTheLevelThatReturnedItWithoutAReviewer() {
        AwardRequest earlier = earlier(RequestStatus.RETURNED, ApprovalLevel.DEAN);
        earlier.setCurrentReviewer(TestUsers.person(31L, "dean@chnu.edu.ua", department));
        earlier.setCompletedAt(NOW.minusSeconds(60));
        when(startLevel.from(ApprovalLevel.DEAN, 69L, 21L)).thenReturn(ApprovalLevel.DEAN);

        AwardResponse response = submission.submit(5L, new SubmitRequest(4L, null));

        assertThat(response.request().status()).isEqualTo(RequestStatus.SUBMITTED);
        assertThat(response.request().currentLevel()).isEqualTo(ApprovalLevel.DEAN);
        assertThat(earlier.getCurrentReviewer()).isNull();
        assertThat(earlier.getCompletedAt()).isNull();
        verify(startLevel, never()).of(anyLong(), anyLong());
    }

    @Test
    void ac3_4_aReturningLevelTheSubmitterNowHoldsAloneIsPassedOver() {
        earlier(RequestStatus.RETURNED, ApprovalLevel.FACULTY_SECRETARY);
        when(startLevel.from(ApprovalLevel.FACULTY_SECRETARY, 69L, 21L)).thenReturn(ApprovalLevel.DEAN);

        AwardResponse response = submission.submit(5L, new SubmitRequest(4L, null));

        assertThat(response.request().currentLevel()).isEqualTo(ApprovalLevel.DEAN);
    }

    @Test
    void ac1_6_ac1_7_aResubmissionSetsANewDeadlineByTheFacultyPeriodInForceNow() {
        Organization faculty = TestUsers.organization(9L, OrganizationType.FACULTY);
        faculty.setReviewWorkingDays(5);
        department.setParent(faculty);
        AwardRequest earlier = earlier(RequestStatus.RETURNED, ApprovalLevel.DEAN);
        earlier.restartPeriod(NOW.minusSeconds(60));
        when(startLevel.from(ApprovalLevel.DEAN, 69L, 21L)).thenReturn(ApprovalLevel.DEAN);

        AwardResponse response = submission.submit(5L, new SubmitRequest(4L, null));

        assertThat(response.request().deadline()).isEqualTo(Instant.parse("2026-10-05T09:00:00Z"));
    }

    private AwardRequest earlier(RequestStatus status, ApprovalLevel level) {
        AwardRequest request = TestAwards.request(draft).status(status).currentLevel(level)
            .submittedAt(NOW.minusSeconds(86_400)).build();
        when(requests.findByAwardIdForUpdate(5L)).thenReturn(Optional.of(request));
        return request;
    }
}
