package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.authz.OrganizationTree;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.delegation.repository.RoleDelegationRepository;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;

class ReviewerAvailabilityTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);
    private static final Set<Long> SCOPES = Set.of(64L, 9L, 1L);
    private static final long OWNER = 21L;
    private static final Set<AccountStatus> SIGNING_IN = EnumSet.of(AccountStatus.ACTIVE, AccountStatus.RETIRED);

    private final UserRoleRepository roles = mock(UserRoleRepository.class);
    private final RoleDelegationRepository delegations = mock(RoleDelegationRepository.class);
    private final OrganizationTree tree = mock(OrganizationTree.class);
    private final ReviewerAvailability availability = new ReviewerAvailability(roles, delegations, tree,
        Clock.fixed(Instant.parse("2026-10-01T09:00:00Z"), ZoneId.of("Europe/Kyiv")));

    @Test
    void ac1_9_anAssignmentInAScopeAboveTheAwardIsAReviewer() {
        when(tree.ancestors(64L)).thenReturn(SCOPES);
        when(roles.existsHolder(RoleType.DEAN, SCOPES, SIGNING_IN, OWNER, TODAY)).thenReturn(true);

        assertThat(availability.hasReviewer(ApprovalLevel.DEAN, 64L, OWNER)).isTrue();
    }

    @Test
    void ac1_9_aDelegateWhoMaySignInStandsInForTheRoleHolder() {
        when(tree.ancestors(64L)).thenReturn(SCOPES);
        when(delegations.existsInEffect(RoleType.DEAN, SCOPES, SIGNING_IN, OWNER, TODAY)).thenReturn(true);

        assertThat(availability.hasReviewer(ApprovalLevel.DEAN, 64L, OWNER)).isTrue();
    }

    @Test
    void ac1_9_withoutAssignmentOrDelegationThereIsNoReviewer() {
        when(tree.ancestors(64L)).thenReturn(SCOPES);

        assertThat(availability.hasReviewer(ApprovalLevel.FACULTY_SECRETARY, 64L, OWNER)).isFalse();
    }

    @Test
    void ac0_6_aLevelHeldOnlyByThePersonIsNotReviewedByAnybodyElse() {
        when(tree.ancestors(64L)).thenReturn(SCOPES);
        when(roles.existsHolder(RoleType.FACULTY_SECRETARY, SCOPES, SIGNING_IN, ReviewerAvailability.NOBODY, TODAY))
            .thenReturn(true);

        assertThat(availability.isHeldOnlyBy(ApprovalLevel.FACULTY_SECRETARY, 64L, OWNER)).isTrue();
    }

    @Test
    void ac0_6_aLevelWithAnotherHolderOrNoHolderIsNotHeldOnlyByThePerson() {
        when(tree.ancestors(64L)).thenReturn(SCOPES);
        when(roles.existsHolder(RoleType.DEAN, SCOPES, SIGNING_IN, OWNER, TODAY)).thenReturn(true);

        assertThat(availability.isHeldOnlyBy(ApprovalLevel.DEAN, 64L, OWNER)).isFalse();
        assertThat(availability.isHeldOnlyBy(ApprovalLevel.FACULTY_SECRETARY, 64L, OWNER)).isFalse();
    }

    @Test
    void edge_anOrganisationMissingFromTheTreeIsItsOwnScope() {
        when(tree.ancestors(700L)).thenReturn(Set.of());
        when(roles.existsHolder(RoleType.RECTOR, Set.of(700L), SIGNING_IN, OWNER, TODAY)).thenReturn(true);

        assertThat(availability.hasReviewer(ApprovalLevel.RECTOR, 700L, OWNER)).isTrue();
    }
}
