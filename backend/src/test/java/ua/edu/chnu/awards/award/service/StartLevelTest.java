package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.award.entity.ApprovalLevel;

class StartLevelTest {

    private static final long FACULTY = 9L;
    private static final long SUBMITTER = 21L;

    private final ReviewerAvailability reviewers = mock(ReviewerAvailability.class);
    private final StartLevel start = new StartLevel(reviewers);

    @Test
    void ac0_6_aRequestStartsAtTheFacultySecretaryWhenSomebodyElseReviewsThere() {
        when(reviewers.isHeldOnlyBy(ApprovalLevel.FACULTY_SECRETARY, FACULTY, SUBMITTER)).thenReturn(false);

        assertThat(start.of(FACULTY, SUBMITTER)).isEqualTo(ApprovalLevel.FACULTY_SECRETARY);
    }

    @Test
    void ac0_6_theOnlySecretaryOfTheFacultyStartsHerOwnAwardAtTheDean() {
        when(reviewers.isHeldOnlyBy(ApprovalLevel.FACULTY_SECRETARY, FACULTY, SUBMITTER)).thenReturn(true);

        assertThat(start.of(FACULTY, SUBMITTER)).isEqualTo(ApprovalLevel.DEAN);
    }

    @Test
    void ac0_6_everyLevelHeldOnlyByTheSubmitterIsPassedOver() {
        when(reviewers.isHeldOnlyBy(ApprovalLevel.FACULTY_SECRETARY, FACULTY, SUBMITTER)).thenReturn(true);
        when(reviewers.isHeldOnlyBy(ApprovalLevel.DEAN, FACULTY, SUBMITTER)).thenReturn(true);

        assertThat(start.of(FACULTY, SUBMITTER)).isEqualTo(ApprovalLevel.RECTOR_SECRETARY);
    }

    @Test
    void ac0_6_aVacantLevelIsNotPassedOver() {
        assertThat(start.of(FACULTY, SUBMITTER)).isEqualTo(ApprovalLevel.FACULTY_SECRETARY);
    }

    @Test
    void edge_theRectorLevelIsKeptEvenWhenOnlyTheSubmitterHoldsIt() {
        for (ApprovalLevel level : ApprovalLevel.values()) {
            when(reviewers.isHeldOnlyBy(level, FACULTY, SUBMITTER)).thenReturn(true);
        }

        assertThat(start.of(FACULTY, SUBMITTER)).isEqualTo(ApprovalLevel.RECTOR);
    }

    @Test
    void edge_aMoveUpPassesOverFromTheGivenLevel() {
        when(reviewers.isHeldOnlyBy(ApprovalLevel.RECTOR_SECRETARY, FACULTY, SUBMITTER)).thenReturn(true);

        assertThat(start.from(ApprovalLevel.RECTOR_SECRETARY, FACULTY, SUBMITTER)).isEqualTo(ApprovalLevel.RECTOR);
    }
}
