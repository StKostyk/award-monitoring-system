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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.award.dto.AwardResponse;
import ua.edu.chnu.awards.award.dto.DuplicateMatch;
import ua.edu.chnu.awards.award.dto.SubmitRequest;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardCategory;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.mapper.AwardMapper;
import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.award.repository.AwardRequestRepository;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.User;

class AwardSubmissionTest {

    private static final Instant NOW = Instant.parse("2026-09-28T09:00:00Z");

    private final AwardRepository awards = mock(AwardRepository.class);
    private final AwardRequestRepository requests = mock(AwardRequestRepository.class);
    private final AwardOwnership ownership = mock(AwardOwnership.class);
    private final AwardInputRules rules = mock(AwardInputRules.class);
    private final AuditService audit = mock(AuditService.class);
    private final DuplicateFinder duplicates = mock(DuplicateFinder.class);
    private final AwardSubmission submission = new AwardSubmission(awards, requests, ownership, rules, duplicates,
        new AwardMapper(), audit, Clock.fixed(NOW, ZoneId.of("Europe/Kyiv")));
    private final Organization oldDepartment = TestUsers.organization(64L, OrganizationType.DEPARTMENT);
    private final Organization newDepartment = TestUsers.organization(69L, OrganizationType.DEPARTMENT);
    private final User owner = TestUsers.person(21L, "owner@chnu.edu.ua", newDepartment);
    private Award draft;

    @BeforeEach
    void setUp() {
        AwardCategory category = AwardCategory.builder().id(13L).name("Ministry")
            .level(RecognitionLevel.NATIONAL).active(true).build();
        draft = Award.builder().id(5L).owner(owner).organization(oldDepartment).category(category)
            .title("Letter").awardingOrganization("MON").awardDate(LocalDate.of(2025, 5, 1)).version(4L).build();
        when(ownership.lockedDraft(5L)).thenReturn(draft);
        when(requests.saveAndFlush(any(AwardRequest.class))).thenAnswer(invocation -> {
            AwardRequest request = invocation.getArgument(0);
            request.setId(40L);
            return request;
        });
    }

    @Test
    void ac1_5_submissionCreatesTheRequestAtTheFacultySecretaryAndAuditsIt() {
        AwardResponse response = submission.submit(5L, new SubmitRequest(4L, null));

        assertThat(draft.getStatus()).isEqualTo(AwardStatus.PENDING);
        assertThat(draft.getImpactScore()).isEqualTo(RecognitionLevel.NATIONAL.baseScore());
        assertThat(response.request().status()).isEqualTo(RequestStatus.SUBMITTED);
        assertThat(response.request().currentLevel()).isEqualTo(ApprovalLevel.FACULTY_SECRETARY);
        assertThat(response.request().submittedAt()).isEqualTo(NOW);
        verify(awards).saveAndFlush(draft);
        verify(audit).record(AuditAction.AWARD_SUBMITTED, AuditEntityConstants.AWARDS, 21L, 5L,
            Map.of("requestId", 40L, "level", "FACULTY_SECRETARY", "organizationId", 69L));
    }

    @Test
    void edge_theOrganisationIsRefreshedFromTheOwnersCurrentDepartment() {
        AwardResponse response = submission.submit(5L, new SubmitRequest(4L, null));

        assertThat(response.organization().id()).isEqualTo(69L);
    }

    @Test
    void ac1_5_anIncompleteDraftIsNotSubmitted() {
        doThrow(new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, "award-incomplete", "missing"))
            .when(rules).checkComplete(draft);

        assertThatThrownBy(() -> submission.submit(5L, new SubmitRequest(4L, null)))
            .isInstanceOf(ApiProblemException.class);
        assertThat(draft.getStatus()).isEqualTo(AwardStatus.DRAFT);
        verify(requests, never()).saveAndFlush(any());
        verify(audit, never()).record(any(), anyString(), anyLong(), anyLong(), anyMap());
    }

    @Test
    void ac2_5_aPossibleDuplicateIsRefusedWithoutAcknowledgementAndNothingChanges() {
        DuplicateMatch match = new DuplicateMatch(9L, "Letter", null, LocalDate.of(2025, 5, 1),
            AwardStatus.PENDING);
        when(duplicates.matches(List.of(5L))).thenReturn(Map.of(5L, List.of(match)));

        assertThatThrownBy(() -> submission.submit(5L, new SubmitRequest(4L, false)))
            .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(e.getType()).isEqualTo("award-possible-duplicate");
                assertThat(e.getProperties()).containsEntry("matches", List.of(match));
            });
        assertThat(draft.getStatus()).isEqualTo(AwardStatus.DRAFT);
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void ac2_5_anAcknowledgedDuplicateIsSubmittedAndTheAuditRowSaysSo() {
        DuplicateMatch match = new DuplicateMatch(9L, "Letter", null, LocalDate.of(2025, 5, 1),
            AwardStatus.PENDING);
        when(duplicates.matches(List.of(5L))).thenReturn(Map.of(5L, List.of(match)));

        submission.submit(5L, new SubmitRequest(4L, true));

        assertThat(draft.getStatus()).isEqualTo(AwardStatus.PENDING);
        verify(audit).record(AuditAction.AWARD_SUBMITTED, AuditEntityConstants.AWARDS, 21L, 5L,
            Map.of("requestId", 40L, "level", "FACULTY_SECRETARY", "organizationId", 69L,
                "duplicateAcknowledged", true));
    }

    @Test
    void ac1_3_theVersionIsCheckedEvenWithoutABody() {
        submission.submit(5L, null);

        verify(ownership).requireVersion(draft, null);
    }
}
