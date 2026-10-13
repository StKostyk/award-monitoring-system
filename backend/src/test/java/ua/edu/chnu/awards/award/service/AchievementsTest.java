package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.award.AwardDocuments;
import ua.edu.chnu.awards.award.dto.Achievement;
import ua.edu.chnu.awards.award.dto.AchievementDetail;
import ua.edu.chnu.awards.award.dto.AchievementQuery;
import ua.edu.chnu.awards.award.dto.RecipientType;
import ua.edu.chnu.awards.award.dto.SharedDocument;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.AwardVisibility;
import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.award.repository.AwardSpecifications;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.entity.OrganizationType;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.organization.service.OrganizationLookup;
import ua.edu.chnu.awards.support.TestAwards;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.User;

class AchievementsTest {

    private static final AchievementQuery ANY = new AchievementQuery(null, null, null, null);

    private final AwardRepository awards = mock(AwardRepository.class);
    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final AwardDocuments documents = mock(AwardDocuments.class);
    private final Achievements achievements = new Achievements(awards, new OrganizationLookup(organizations),
        new AwardSpecifications(),
        documents);
    private final Organization department = TestUsers.organization(64L, OrganizationType.DEPARTMENT);
    private final User owner = TestUsers.person(21L, "owner@chnu.edu.ua", department);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(awards.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
    }

    @Test
    void ac2_aSharedPersonalAwardShowsWithTheDocumentsItsOwnerShows() {
        Award award = TestAwards.award(owner, department).status(AwardStatus.APPROVED)
            .visibility(AwardVisibility.PUBLIC).build();
        SharedDocument shown = new SharedDocument(7L, "диплом.pdf", "DIPLOMA", "application/pdf", 3L, null);
        when(awards.findWithDetailsById(TestAwards.AWARD_ID)).thenReturn(Optional.of(award));
        when(documents.shownToColleagues(TestAwards.AWARD_ID)).thenReturn(List.of(shown));

        AchievementDetail detail = achievements.detail(TestAwards.AWARD_ID);

        assertThat(detail.achievement().awardId()).isEqualTo(TestAwards.AWARD_ID);
        assertThat(detail.documents()).extracting(SharedDocument::fileName).containsExactly("диплом.pdf");
    }

    @Test
    void ac2_anApprovedUnitAwardShowsWithoutDocuments() {
        Award award = TestAwards.award(owner, department).status(AwardStatus.APPROVED)
            .recipientOrganizationId(department.getId()).build();
        when(awards.findWithDetailsById(TestAwards.AWARD_ID)).thenReturn(Optional.of(award));

        assertThat(achievements.detail(TestAwards.AWARD_ID).documents()).isEmpty();
        verifyNoInteractions(documents);
    }

    @Test
    void ac2_anAwardThatIsNotSharedIsNotFound() {
        Award privateAward = TestAwards.award(owner, department).status(AwardStatus.APPROVED).build();
        Award pendingUnit = TestAwards.award(owner, department).status(AwardStatus.PENDING)
            .recipientOrganizationId(department.getId()).build();

        for (Award award : List.of(privateAward, pendingUnit)) {
            when(awards.findWithDetailsById(TestAwards.AWARD_ID)).thenReturn(Optional.of(award));
            assertThatThrownBy(() -> achievements.detail(TestAwards.AWARD_ID))
                .isInstanceOf(AwardNotFoundException.class);
        }
        assertThatThrownBy(() -> achievements.detail(99L)).isInstanceOf(AwardNotFoundException.class);
        verifyNoInteractions(documents);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ac1_5_aSharedPersonalAwardShowsOnlyThePublicProjectionNewestFirst() {
        Award award = TestAwards.award(owner, department).status(AwardStatus.APPROVED)
            .visibility(AwardVisibility.UNIVERSITY).verificationBadge(true).impactScore(80)
            .awardDate(LocalDate.of(2026, 5, 4)).build();
        when(awards.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(award)));

        Page<Achievement> page = achievements.shared(ANY, 0, 20);

        Achievement achievement = page.getContent().getFirst();
        assertThat(achievement.awardId()).isEqualTo(TestAwards.AWARD_ID);
        assertThat(achievement.verified()).isTrue();
        assertThat(achievement.recipient().type()).isEqualTo(RecipientType.PERSON);
        assertThat(achievement.recipient().personName()).isEqualTo(owner.getFullName());
        assertThat(achievement.recipient().unit().id()).isEqualTo(department.getId());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(awards).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue()).isEqualTo(PageRequest.of(0, 20,
            Sort.by(Sort.Order.desc("awardDate"), Sort.Order.desc("id"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ac1_5_aUnitAwardNamesNoPerson() {
        Award award = TestAwards.award(owner, department).status(AwardStatus.APPROVED)
            .recipientOrganizationId(department.getId()).build();
        when(awards.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(award)));

        Achievement achievement = achievements.shared(ANY, 0, 20).getContent().getFirst();

        assertThat(achievement.recipient().type()).isEqualTo(RecipientType.UNIT);
        assertThat(achievement.recipient().personName()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void ac2_1_thePublishedListValidatesTheFiltersLikeTheSignedInOne() {
        when(organizations.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> achievements.published(new AchievementQuery(99L, null, null, null), 0, 20))
            .isInstanceOf(UnitNotFoundException.class);
        assertThat(achievements.published(ANY, 0, 20)).isEmpty();
        verify(awards).findAll(any(Specification.class), any(Pageable.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {1949, 2101})
    @SuppressWarnings("unchecked")
    void ac1_6_aYearOutsideTheRangeIsAnInvalidParameter(int year) {
        assertThatThrownBy(() -> achievements.shared(new AchievementQuery(null, year, null, null), 0, 20))
            .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(problem.getType()).isEqualTo("invalid-parameter");
            });
        verify(awards, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void ac1_6_anUnknownUnitIsNotFound() {
        when(organizations.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> achievements.shared(new AchievementQuery(99L, null, null, null), 0, 20))
            .isInstanceOf(UnitNotFoundException.class);
    }

    @Test
    void ac1_6_aUnitThatIsNeitherFacultyNorDepartmentIsNotFound() {
        Organization university = TestUsers.organization(1L, OrganizationType.UNIVERSITY);
        when(organizations.findById(1L)).thenReturn(Optional.of(university));

        assertThatThrownBy(() -> achievements.shared(new AchievementQuery(1L, null, null, null), 0, 20))
            .isInstanceOf(UnitNotFoundException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ac1_6_aFacultyIsAValidUnit() {
        Organization faculty = TestUsers.organization(10L, OrganizationType.FACULTY);
        when(organizations.findById(10L)).thenReturn(Optional.of(faculty));

        assertThat(achievements.shared(new AchievementQuery(10L, 2026, null, null), 0, 20)).isEmpty();
        verify(awards).findAll(any(Specification.class), any(Pageable.class));
    }
}
