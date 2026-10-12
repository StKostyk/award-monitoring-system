package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.User;

class TakeOverRuleTest {

    private static final long FACULTY = 9L;

    private final ReviewerRule rule = mock(ReviewerRule.class);
    private final ReviewerAvailability availability = mock(ReviewerAvailability.class);
    private final AccessScope access = mock(AccessScope.class);
    private final TakeOverRule takeOver = new TakeOverRule(rule, availability, access);

    private final Organization faculty = TestUsers.faculty(FACULTY);
    private final User owner = TestUsers.person(7L, "employee@chnu.edu.ua", faculty);
    private final User caller = TestUsers.person(5L, "secretary@chnu.edu.ua", faculty);
    private final User holder = TestUsers.person(6L, "secretary2@chnu.edu.ua", faculty);
    private AwardRequest request;

    @BeforeEach
    void setUp() {
        Award award = Award.builder().id(1L).owner(owner).organization(faculty).build();
        request = AwardRequest.builder().id(2L).award(award).submitter(owner).status(RequestStatus.IN_REVIEW)
            .currentLevel(ApprovalLevel.FACULTY_SECRETARY).currentReviewer(holder).build();
        when(access.callerId()).thenReturn(caller.getId());
        when(rule.highestLevel(request)).thenReturn(Optional.of(ApprovalLevel.FACULTY_SECRETARY));
    }

    @Test
    void ac1_aPeerIsOfferedTheRequestOfAHolderWhoLostTheRole() {
        assertThat(takeOver.offered(request)).isTrue();
    }

    @Test
    void ac2_aPeerIsNotOfferedTheRequestOfAHolderWhoIsStillEligible() {
        when(availability.isEligible(holder.getId(), ApprovalLevel.FACULTY_SECRETARY, FACULTY, owner.getId(),
            owner.getId())).thenReturn(true);

        assertThat(takeOver.offered(request)).isFalse();
    }

    @Test
    void ac1_nothingIsOfferedForAnUnclaimedOrOwnRequest() {
        request.setCurrentReviewer(null);
        assertThat(takeOver.offered(request)).isFalse();

        request.setCurrentReviewer(caller);
        assertThat(takeOver.offered(request)).isFalse();
    }
}
