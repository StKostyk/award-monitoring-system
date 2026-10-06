package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.authz.DelegatedScope;
import ua.edu.chnu.awards.authz.OrganizationTree;
import ua.edu.chnu.awards.authz.RoleScope;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardRequest;
import ua.edu.chnu.awards.award.entity.RequestStatus;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;

class ReviewerRuleTest {

    private static final long CALLER = 5L;
    private static final long OWNER = 7L;
    private static final long DEAN = 8L;
    private static final long FACULTY = 9L;
    private static final long DEPARTMENT = 64L;
    private static final long OTHER_FACULTY = 10L;

    private final AccessScope access = mock(AccessScope.class);
    private final OrganizationTree tree = mock(OrganizationTree.class);
    private final ReviewerRule rule = new ReviewerRule(access, tree);
    private final Organization department = TestUsers.organization(DEPARTMENT, OrganizationType.DEPARTMENT);
    private final User owner = TestUsers.person(OWNER, "owner@chnu.edu.ua", department);

    @BeforeEach
    void setUp() {
        when(access.callerId()).thenReturn(CALLER);
        when(access.heldScopes()).thenReturn(List.of());
        when(access.delegations()).thenReturn(List.of());
        when(tree.covers(anyLong(), anyLong())).thenReturn(false);
        when(tree.covers(FACULTY, DEPARTMENT)).thenReturn(true);
        when(tree.covers(DEPARTMENT, DEPARTMENT)).thenReturn(true);
    }

    @Test
    void ac1_1_anOwnRoleWhoseScopeCoversTheAwardReviewsItsLevel() {
        held(RoleType.FACULTY_SECRETARY, FACULTY);

        assertThat(rule.grant(request(ApprovalLevel.FACULTY_SECRETARY, owner)))
            .hasValueSatisfying(grant -> {
                assertThat(grant.level()).isEqualTo(ApprovalLevel.FACULTY_SECRETARY);
                assertThat(grant.delegatorId()).isNull();
            });
    }

    @Test
    void ac1_1_aDelegatedRoleReviewsOnBehalfOfItsDelegator() {
        when(access.delegations()).thenReturn(List.of(new DelegatedScope(RoleType.DEAN, FACULTY, DEAN)));

        assertThat(rule.grant(request(ApprovalLevel.DEAN, owner)))
            .hasValueSatisfying(grant -> assertThat(grant.delegatorId()).isEqualTo(DEAN));
    }

    @Test
    void ac1_1_anOwnRoleIsPreferredToADelegationOfTheSameReach() {
        held(RoleType.DEAN, FACULTY);
        when(access.delegations()).thenReturn(List.of(new DelegatedScope(RoleType.DEAN, FACULTY, DEAN)));

        assertThat(rule.grant(request(ApprovalLevel.DEAN, owner)))
            .hasValueSatisfying(grant -> assertThat(grant.delegated()).isFalse());
    }

    @Test
    void ac1_2_aHigherLevelMayReviewALowerOneButNotTheOtherWayRound() {
        held(RoleType.FACULTY_SECRETARY, FACULTY);
        assertThat(rule.grant(request(ApprovalLevel.DEAN, owner))).isEmpty();

        held(RoleType.DEAN, FACULTY);
        AwardRequest secretaryLevel = request(ApprovalLevel.FACULTY_SECRETARY, owner);
        assertThat(rule.grant(secretaryLevel)).isPresent();
        assertThat(rule.highestLevel(secretaryLevel)).contains(ApprovalLevel.DEAN);
    }

    @Test
    void ac1_1_aScopeElsewhereGrantsNothing() {
        held(RoleType.DEAN, OTHER_FACULTY);

        assertThat(rule.grant(request(ApprovalLevel.FACULTY_SECRETARY, owner))).isEmpty();
    }

    @Test
    void ac1_1_nobodyReviewsAnAwardTheyOwn() {
        held(RoleType.FACULTY_SECRETARY, FACULTY);
        User caller = TestUsers.person(CALLER, "secretary@chnu.edu.ua", department);

        assertThat(rule.grant(request(ApprovalLevel.FACULTY_SECRETARY, caller))).isEmpty();
    }

    @Test
    void ac1_1_nobodyReviewsARequestTheySubmitted() {
        held(RoleType.FACULTY_SECRETARY, FACULTY);
        AwardRequest request = request(ApprovalLevel.FACULTY_SECRETARY, owner);
        request = AwardRequest.builder().award(request.getAward())
            .submitter(TestUsers.person(CALLER, "dean@chnu.edu.ua", department))
            .status(RequestStatus.SUBMITTED).currentLevel(ApprovalLevel.FACULTY_SECRETARY).build();

        assertThat(rule.grant(request)).isEmpty();
    }

    @Test
    void ac1_1_aDelegationLentByTheOwnerDoesNotLetTheDelegateReviewTheOwnersAward() {
        when(access.delegations()).thenReturn(List.of(new DelegatedScope(RoleType.DEAN, FACULTY, OWNER)));

        assertThat(rule.grant(request(ApprovalLevel.DEAN, owner))).isEmpty();
    }

    @Test
    void ac1_3_onlyApprovalRolesMakeAnApprover() {
        held(RoleType.EMPLOYEE, DEPARTMENT);
        assertThat(rule.callerGrants()).isEmpty();

        when(access.delegations()).thenReturn(List.of(new DelegatedScope(RoleType.FACULTY_SECRETARY, FACULTY,
            DEAN)));
        assertThat(rule.callerGrants()).singleElement()
            .satisfies(grant -> assertThat(grant.organizationId()).isEqualTo(FACULTY));
    }

    private void held(RoleType role, long organizationId) {
        when(access.heldScopes()).thenReturn(List.of(new RoleScope(role, organizationId)));
    }

    private AwardRequest request(ApprovalLevel level, User awardOwner) {
        Award award = Award.builder().id(1L).owner(awardOwner).organization(department).build();
        return AwardRequest.builder().id(2L).award(award).submitter(awardOwner).status(RequestStatus.SUBMITTED)
            .currentLevel(level).build();
    }
}
