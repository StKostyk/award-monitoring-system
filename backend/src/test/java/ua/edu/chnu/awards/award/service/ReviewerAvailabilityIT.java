package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import ua.edu.chnu.awards.authz.OrganizationTree;
import ua.edu.chnu.awards.award.entity.ApprovalLevel;
import ua.edu.chnu.awards.delegation.entity.RoleDelegation;
import ua.edu.chnu.awards.delegation.repository.RoleDelegationRepository;
import ua.edu.chnu.awards.support.AbstractJpaSliceTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;

class ReviewerAvailabilityIT extends AbstractJpaSliceTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRoleRepository roles;

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private RoleDelegationRepository delegations;

    private final Clock clock = Clock.system(ZoneId.of("Europe/Kyiv"));
    private final LocalDate today = LocalDate.now(clock);
    private ReviewerAvailability availability;
    private Organization root;
    private Organization faculty;
    private Organization department;
    private User owner;

    @BeforeEach
    void seed() {
        root = organization("IT root", OrganizationType.UNIVERSITY, null);
        faculty = organization("IT faculty", OrganizationType.FACULTY, root);
        department = organization("IT department", OrganizationType.DEPARTMENT, faculty);
        entityManager.flush();
        OrganizationTree tree = new OrganizationTree(organizations);
        tree.refresh();
        availability = new ReviewerAvailability(roles, delegations, tree, clock);
        owner = person("owner");
    }

    @Test
    void ac1_9_withoutAnyHolderTheLevelHasNoReviewer() {
        User former = person("former.secretary");
        role(former, RoleType.FACULTY_SECRETARY, faculty, today.minusYears(1), today.minusDays(1));
        role(person("future.secretary"), RoleType.FACULTY_SECRETARY, faculty, today.plusDays(1), null);
        role(person("other.dean"), RoleType.DEAN, faculty, today.minusYears(1), null);

        assertThat(reviewable(ApprovalLevel.FACULTY_SECRETARY)).isFalse();
    }

    @Test
    void ac1_9_anAssignmentAtTheFacultyCoversItsDepartments() {
        role(person("secretary"), RoleType.FACULTY_SECRETARY, faculty, today.minusYears(1), null);

        assertThat(reviewable(ApprovalLevel.FACULTY_SECRETARY)).isTrue();
        assertThat(reviewable(ApprovalLevel.DEAN)).isFalse();
    }

    @Test
    void ac1_9_aHolderWhoCannotSignInIsNoReviewer() {
        User suspended = person("suspended.secretary");
        suspended.setAccountStatus(AccountStatus.SUSPENDED);
        role(suspended, RoleType.FACULTY_SECRETARY, faculty, today.minusYears(1), null);

        assertThat(reviewable(ApprovalLevel.FACULTY_SECRETARY)).isFalse();
    }

    @Test
    void ac1_9_theOwnerHoldingTheRoleIsNoReviewerOfTheirOwnAward() {
        role(owner, RoleType.FACULTY_SECRETARY, faculty, today.minusYears(1), null);

        assertThat(reviewable(ApprovalLevel.FACULTY_SECRETARY)).isFalse();
        assertThat(availability.hasReviewer(ApprovalLevel.FACULTY_SECRETARY, department.getId(), -1L)).isTrue();
    }

    @Test
    void ac0_6_theOnlySecretaryHoldsHerLevelAloneUntilASecondOneArrives() {
        role(owner, RoleType.FACULTY_SECRETARY, faculty, today.minusYears(1), null);

        assertThat(availability.isHeldOnlyBy(ApprovalLevel.FACULTY_SECRETARY, department.getId(), owner.getId()))
            .isTrue();
        assertThat(availability.isHeldOnlyBy(ApprovalLevel.DEAN, department.getId(), owner.getId())).isFalse();

        role(person("second.secretary"), RoleType.FACULTY_SECRETARY, faculty, today.minusYears(1), null);

        assertThat(availability.isHeldOnlyBy(ApprovalLevel.FACULTY_SECRETARY, department.getId(), owner.getId()))
            .isFalse();
    }

    @Test
    void ac0_6_aDelegateOfTheOwnerDoesNotReviewTheOwnersAward() {
        role(owner, RoleType.FACULTY_SECRETARY, faculty, today.minusYears(1), null);
        entityManager.persist(RoleDelegation.builder().delegator(owner).delegate(person("assistant"))
            .roleType(RoleType.FACULTY_SECRETARY).organization(faculty).validFrom(today.minusDays(1))
            .validTo(today.plusDays(5)).reason("Leave").build());
        entityManager.flush();

        assertThat(reviewable(ApprovalLevel.FACULTY_SECRETARY)).isFalse();
        assertThat(availability.isHeldOnlyBy(ApprovalLevel.FACULTY_SECRETARY, department.getId(), owner.getId()))
            .isTrue();
    }

    @Test
    void ac1_9_aDelegateStandsInForASuspendedDelegator() {
        User dean = person("suspended.dean");
        dean.setAccountStatus(AccountStatus.SUSPENDED);
        role(dean, RoleType.DEAN, faculty, today.minusYears(1), null);
        assertThat(reviewable(ApprovalLevel.DEAN)).isFalse();

        entityManager.persist(RoleDelegation.builder().delegator(dean).delegate(person("acting.dean"))
            .roleType(RoleType.DEAN).organization(faculty).validFrom(today.minusDays(1)).validTo(today.plusDays(5))
            .reason("Leave").build());
        entityManager.flush();

        assertThat(reviewable(ApprovalLevel.DEAN)).isTrue();
    }

    @Test
    void ac1_9_aUniversityWideHolderCoversEveryUnit() {
        role(person("rector.secretary"), RoleType.RECTOR_SECRETARY, root, today.minusYears(1), null);

        assertThat(reviewable(ApprovalLevel.RECTOR_SECRETARY)).isTrue();
    }

    @Test
    void ac1_9_aDelegationOutlivingItsDelegatorsRoleGivesNoReviewer() {
        User dean = person("dean");
        role(dean, RoleType.DEAN, faculty, today.minusYears(1), null);
        entityManager.persist(RoleDelegation.builder().delegator(dean).delegate(person("deputy"))
            .roleType(RoleType.DEAN).organization(faculty).validFrom(today.minusDays(1)).validTo(today.plusDays(5))
            .reason("Leave").build());
        entityManager.flush();
        assertThat(reviewable(ApprovalLevel.DEAN)).isTrue();

        entityManager.getEntityManager().createQuery("update UserRole r set r.validTo = :day where r.user = :dean")
            .setParameter("day", today.minusDays(1)).setParameter("dean", dean).executeUpdate();

        assertThat(reviewable(ApprovalLevel.DEAN)).isFalse();
    }

    @Test
    void ac1_8_theCandidatesAreHoldersAndDelegatesButNotTheOwnerOrSubmitter() {
        User secretary = person("first.secretary");
        User colleague = person("second.secretary");
        User blocked = person("blocked.secretary");
        blocked.setAccountStatus(AccountStatus.SUSPENDED);
        User submitter = person("submitter");
        User deputy = person("deputy.secretary");
        final User ownersDeputy = person("owners.deputy");
        for (User holder : List.of(secretary, colleague, blocked, submitter, owner)) {
            role(holder, RoleType.FACULTY_SECRETARY, faculty, today.minusYears(1), null);
        }
        delegate(secretary, deputy);
        delegate(colleague, secretary);
        delegate(owner, ownersDeputy);

        assertThat(availability.candidates(ApprovalLevel.FACULTY_SECRETARY, department.getId(), owner.getId(),
                submitter.getId()))
            .extracting(ReviewerAvailability.Candidate::id, ReviewerAvailability.Candidate::delegated)
            .containsExactlyInAnyOrder(tuple(secretary.getId(), false),
                tuple(colleague.getId(), false),
                tuple(deputy.getId(), true));
    }

    @Test
    void ac1_6_aDeanStaysEligibleForTheSecretaryLevelUntilTheRoleEnds() {
        User dean = person("eligible.dean");
        role(dean, RoleType.DEAN, faculty, today.minusYears(1), null);
        assertThat(availability.isEligible(dean.getId(), ApprovalLevel.FACULTY_SECRETARY, department.getId(),
            owner.getId(), owner.getId())).isTrue();
        assertThat(availability.isEligible(dean.getId(), ApprovalLevel.RECTOR_SECRETARY, department.getId(),
            owner.getId(), owner.getId())).isFalse();

        entityManager.getEntityManager().createQuery("update UserRole r set r.validTo = :day where r.user = :dean")
            .setParameter("day", today.minusDays(1)).setParameter("dean", dean).executeUpdate();

        assertThat(availability.isEligible(dean.getId(), ApprovalLevel.FACULTY_SECRETARY, department.getId(),
            owner.getId(), owner.getId())).isFalse();
    }

    private void delegate(User delegator, User delegate) {
        entityManager.persist(RoleDelegation.builder().delegator(delegator).delegate(delegate)
            .roleType(RoleType.FACULTY_SECRETARY).organization(faculty).validFrom(today.minusDays(1))
            .validTo(today.plusDays(5)).reason("Leave").build());
        entityManager.flush();
    }

    private boolean reviewable(ApprovalLevel level) {
        return availability.hasReviewer(level, department.getId(), owner.getId());
    }

    private Organization organization(String name, OrganizationType type, Organization parent) {
        return entityManager.persist(Organization.builder().name(name).nameUk(name).code("IT-" + name.hashCode())
            .orgType(type).parent(parent).depth(parent == null ? 0 : parent.getDepth() + 1).active(true).build());
    }

    private User person(String name) {
        return entityManager.persist(TestUsers.user("availability." + name + "@chnu.edu.ua", department));
    }

    private void role(User user, RoleType type, Organization organization, LocalDate from, LocalDate to) {
        entityManager.persist(TestUsers.role(user, type, organization, from, to));
        entityManager.flush();
    }
}
