package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.entity.ReviewDecisionType;
import ua.edu.chnu.awards.award.mapper.AwardMapper;
import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.award.repository.AwardRequestRepository;
import ua.edu.chnu.awards.award.repository.AwardSpecifications;
import ua.edu.chnu.awards.award.repository.ReviewDecisionRepository;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.document.service.DocumentService;
import ua.edu.chnu.awards.support.TestAwards;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.support.TestWorkflow;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.repository.UserRepository;

class ReturnedDraftTest {

    private final AwardRepository awards = mock(AwardRepository.class);
    private final AwardRequestRepository requests = mock(AwardRequestRepository.class);
    private final AccessScope access = mock(AccessScope.class);
    private final DocumentService documents = mock(DocumentService.class);
    private final ReviewDecisionRepository decisions = mock(ReviewDecisionRepository.class);
    private final AwardService service = new AwardService(awards, new RequestLookup(requests, decisions),
        mock(AwardSpecifications.class), mock(AwardInputRules.class),
        TestAwards.ownership(awards, mock(UserRepository.class), access), mock(AwardWarnings.class),
        new AwardMapper(TestWorkflow.estimator()), access, mock(AwardHistory.class), documents);
    private final Organization department = TestUsers.organization(64L, OrganizationType.DEPARTMENT);
    private Award draft;

    @BeforeEach
    void setUp() {
        draft = TestAwards.award(TestUsers.person(21L, "owner@chnu.edu.ua", department), department).build();
        when(access.callerId()).thenReturn(21L);
        when(awards.findForUpdate(5L)).thenReturn(Optional.of(draft));
        when(awards.findWithDetailsById(5L)).thenReturn(Optional.of(draft));
    }

    @Test
    void edge_aReturnedOrWithdrawnDraftKeepsItsHistoryAndIsNotDeleted() {
        request(RequestStatus.WITHDRAWN);

        assertThatThrownBy(() -> service.delete(5L)).isInstanceOfSatisfying(ApiProblemException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(e.getType()).isEqualTo("award-has-request");
        });
        verify(awards, never()).delete(any(Award.class));
        verify(documents, never()).releaseObjectsOf(anyLong());
    }

    @Test
    void ac3_5_aReturnedDraftCarriesTheCommentOfTheLatestReturn() {
        request(RequestStatus.RETURNED);
        when(decisions.findFirstByRequestIdAndDecisionOrderByDecidedAtDescIdDesc(TestAwards.REQUEST_ID,
            ReviewDecisionType.RETURNED)).thenReturn(Optional.of(TestAwards.decision(8L, ReviewDecisionType.RETURNED,
                ApprovalLevel.DEAN, TestUsers.person(31L, "dean@chnu.edu.ua", department), Instant.EPOCH)));

        assertThat(service.get(5L).request().returnComment()).isEqualTo("comment 8");
    }

    @Test
    void ac3_5_aWithdrawnDraftCarriesNoReturnComment() {
        request(RequestStatus.WITHDRAWN);

        assertThat(service.get(5L).request().returnComment()).isNull();
        verify(decisions, never()).findFirstByRequestIdAndDecisionOrderByDecidedAtDescIdDesc(anyLong(), any());
    }

    private void request(RequestStatus status) {
        when(requests.findByAwardId(5L)).thenReturn(Optional.of(TestAwards.request(draft).status(status).build()));
    }
}
