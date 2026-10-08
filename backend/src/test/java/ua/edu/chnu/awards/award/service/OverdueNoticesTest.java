package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.repository.AwardRequestRepository;
import ua.edu.chnu.awards.award.service.ReviewerAvailability.Candidate;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.OrganizationType;

class OverdueNoticesTest {

    private static final long DEPARTMENT_ID = 64L;
    private static final long OWNER_ID = 21L;
    private static final long SUBMITTER_ID = 22L;
    private static final Candidate RECTOR_OFFICE = new Candidate(40L, "Ольга Мельник", "office@chnu.edu.ua", false);

    private final ReviewerAvailability availability = mock(ReviewerAvailability.class);
    private final OverdueNotices notices = new OverdueNotices(mock(JdbcTemplate.class),
        mock(AwardRequestRepository.class), availability, mock(AuditService.class), mock(ReviewMetrics.class),
        mock(ApplicationEventPublisher.class));

    @Test
    void ac2_2_aVacantLevelIsSkippedForTheNextLevelWithAReviewer() {
        when(availability.candidates(ApprovalLevel.DEAN, DEPARTMENT_ID, OWNER_ID, SUBMITTER_ID)).thenReturn(List.of());
        when(availability.candidates(ApprovalLevel.RECTOR_SECRETARY, DEPARTMENT_ID, OWNER_ID, SUBMITTER_ID))
            .thenReturn(List.of(RECTOR_OFFICE));

        assertThat(notices.recipients(request(ApprovalLevel.FACULTY_SECRETARY))).containsExactly(RECTOR_OFFICE);
        verify(availability, never()).candidates(ApprovalLevel.RECTOR, DEPARTMENT_ID, OWNER_ID, SUBMITTER_ID);
    }

    @Test
    void ac2_2_aRequestAtTheRectorOrWithoutReviewerAboveHasNoRecipient() {
        when(availability.candidates(any(), anyLong(), anyLong(), anyLong())).thenReturn(List.of());

        assertThat(notices.recipients(request(ApprovalLevel.RECTOR))).isEmpty();
        assertThat(notices.recipients(request(ApprovalLevel.RECTOR_SECRETARY))).isEmpty();
        verify(availability).candidates(ApprovalLevel.RECTOR, DEPARTMENT_ID, OWNER_ID, SUBMITTER_ID);
    }

    private static AwardRequest request(ApprovalLevel level) {
        Award award = mock(Award.class);
        when(award.getOrganization()).thenReturn(TestUsers.organization(DEPARTMENT_ID, OrganizationType.DEPARTMENT));
        when(award.getOwner()).thenReturn(TestUsers.person(OWNER_ID, "owner@chnu.edu.ua"));
        AwardRequest request = mock(AwardRequest.class);
        when(request.getAward()).thenReturn(award);
        when(request.getSubmitter()).thenReturn(TestUsers.person(SUBMITTER_ID, "submitter@chnu.edu.ua"));
        when(request.getCurrentLevel()).thenReturn(level);
        return request;
    }
}
