package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.award.event.ReviewTakenOver;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.User;

class TakeOverRuleTest {

    private static final long FACULTY = 9L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC);

    private final ReviewerRule rule = mock(ReviewerRule.class);
    private final ReviewerAvailability availability = mock(ReviewerAvailability.class);
    private final AccessScope access = mock(AccessScope.class);
    private final StatusEstimator estimator = mock(StatusEstimator.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final TakeOverRule takeOver = new TakeOverRule(rule, availability, access, estimator, events,
        CLOCK);

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
        overdue(false);
    }

    @Test
    void ac1_aPeerIsOfferedTheRequestOfAHolderWhoLostTheRole() {
        assertThat(takeOver.offered(request)).isTrue();
    }

    @Test
    void ac2_aPeerIsNotOfferedTheRequestOfAHolderWhoIsStillEligible() {
        eligible(ApprovalLevel.FACULTY_SECRETARY);

        assertThat(takeOver.offered(request)).isFalse();
    }

    @Test
    void ac1_nothingIsOfferedForAnUnclaimedOrOwnRequest() {
        request.setCurrentReviewer(null);
        assertThat(takeOver.offered(request)).isFalse();

        request.setCurrentReviewer(caller);
        assertThat(takeOver.offered(request)).isFalse();
    }

    @Test
    void ac1_aPeerMayTakeOverAnOverdueRequestOfAnEligibleHolder() {
        eligible(ApprovalLevel.FACULTY_SECRETARY);
        overdue(true);

        assertThat(takeOver.offered(request)).isTrue();
        assertThat(takeOver.allows(request, holder)).isTrue();
    }

    @Test
    void ac1_aLowerLevelMayNotTakeOverAnOverdueRequestFromAHigherHolder() {
        eligible(ApprovalLevel.FACULTY_SECRETARY);
        eligible(ApprovalLevel.DEAN);
        overdue(true);

        assertThat(takeOver.allows(request, holder)).isFalse();
    }

    @Test
    void ac1_aHigherLevelTakesOverBeforeTheDeadline() {
        eligible(ApprovalLevel.FACULTY_SECRETARY);
        when(rule.highestLevel(request)).thenReturn(Optional.of(ApprovalLevel.DEAN));

        assertThat(takeOver.allows(request, holder)).isTrue();
    }

    @Test
    void ac2_thePreviousHolderIsToldWhoTookTheRequestOver() {
        request.getAward().setTitleUk("Грамота");

        takeOver.announce(request, holder, caller);

        verify(events).publishEvent(new ReviewTakenOver(holder.getId(), holder.getEmailAddress(),
            holder.getFirstName(), 1L, "Грамота", "Грамота", caller.getFullName(), CLOCK.instant()));
    }

    private void eligible(ApprovalLevel level) {
        when(availability.isEligible(holder.getId(), level, FACULTY, owner.getId(), owner.getId())).thenReturn(true);
    }

    private void overdue(boolean overdue) {
        when(estimator.timeline(request.getAward(), request)).thenReturn(new StatusEstimator.Timeline(
            List.of(ApprovalLevel.FACULTY_SECRETARY), Map.of(), null, null, overdue));
    }
}
