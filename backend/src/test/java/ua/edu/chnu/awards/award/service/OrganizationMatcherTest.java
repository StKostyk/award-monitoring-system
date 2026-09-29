package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import ua.edu.chnu.awards.authz.OrganizationTree;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrganizationMatcherTest {

    @Mock
    private OrganizationRepository repository;

    @Mock
    private OrganizationTree tree;

    @InjectMocks
    private OrganizationMatcher matcher;

    @BeforeEach
    void setUp() {
        for (long unit : List.of(14L, 9L, 13L, 64L, 97L)) {
            when(tree.covers(1L, unit)).thenReturn(true);
        }
        when(tree.covers(9L, 64L)).thenReturn(true);
        when(tree.covers(14L, 97L)).thenReturn(true);
        when(repository.findByActiveTrue()).thenReturn(List.of(
            unit(1, OrganizationType.UNIVERSITY, 0, "Yuriy Fedkovych Chernivtsi National University",
                "Чернівецький національний університет імені Юрія Федьковича", "ChNU"),
            unit(14, OrganizationType.COLLEGE, 1, "Applied College of Yuriy Fedkovych Chernivtsi National University",
                "Фаховий коледж Чернівецького національного університету імені Юрія Федьковича", "ACChNU"),
            unit(9, OrganizationType.FACULTY, 1, "Faculty of Mathematics and Computer Science",
                "Факультет математики та інформатики", "FMI"),
            unit(13, OrganizationType.FACULTY, 1, "Faculty of Law", "Юридичний факультет", "FL"),
            unit(64, OrganizationType.DEPARTMENT, 2, "Department of Algebra and Informatics",
                "Кафедра алгебри та інформатики", "DAI"),
            unit(97, OrganizationType.SPECIALITY, 2, "Computer Science", "Компʼютерні науки", "SCS")));
    }

    @Test
    void ac3_1_aDepartmentNamedInAnInflectedFormGivesTheDepartmentLevel() {
        assertThat(matcher.level(SuggestionText.of("Кафедри алгебри та інформатики ЧНУ")))
            .contains(RecognitionLevel.DEPARTMENT);
    }

    @Test
    void ac3_1_aFacultyGivesTheFacultyLevelThoughADepartmentSharesAWord() {
        assertThat(matcher.level(SuggestionText.of("Факультет математики та інформатики ЧНУ")))
            .contains(RecognitionLevel.FACULTY);
        assertThat(matcher.level(SuggestionText.of("Юридичного факультету"))).contains(RecognitionLevel.FACULTY);
    }

    @Test
    void ac3_1_theUniversityAloneGivesTheUniversityLevel() {
        assertThat(matcher.level(SuggestionText.of("Чернівецький національний університет")))
            .contains(RecognitionLevel.UNIVERSITY);
        assertThat(matcher.level(SuggestionText.of("Yuriy Fedkovych Chernivtsi National University")))
            .contains(RecognitionLevel.UNIVERSITY);
        assertThat(matcher.level(SuggestionText.of("Rector's office, ChNU"))).contains(RecognitionLevel.UNIVERSITY);
    }

    @Test
    void ac3_1_theCollegeWinsOverTheUniversityItsNameContains() {
        assertThat(matcher.level(SuggestionText.of("Фаховий коледж Чернівецького національного університету")))
            .contains(RecognitionLevel.COLLEGE);
        assertThat(matcher.level(SuggestionText.of(
            "Applied College of Yuriy Fedkovych Chernivtsi National University"))).contains(RecognitionLevel.COLLEGE);
    }

    @Test
    void ac3_1_theBetterCoveredOfTwoMatchesWins() {
        assertThat(matcher.level(SuggestionText.of("Computer Science study programme")))
            .contains(RecognitionLevel.SPECIALITY);
        assertThat(matcher.level(SuggestionText.of("Faculty of Mathematics and Computer Science")))
            .contains(RecognitionLevel.FACULTY);
    }

    @Test
    void ac3_1_halfOfANameOrAnOutsideOrganisationIsNoMatch() {
        assertThat(matcher.level(SuggestionText.of("Кафедра інформатики"))).isEmpty();
        assertThat(matcher.level(SuggestionText.of("Чернівецька міська рада"))).isEmpty();
        assertThat(matcher.level(SuggestionText.of("IEEE International Conference"))).isEmpty();
    }

    @Test
    void ac3_1_aUnitNamedWithTheFullUniversityNameKeepsItsOwnLevel() {
        assertThat(matcher.level(SuggestionText.of(
            "Faculty of Mathematics and Computer Science, Yuriy Fedkovych Chernivtsi National University")))
            .contains(RecognitionLevel.FACULTY);
        assertThat(matcher.level(SuggestionText.of(
            "Кафедра алгебри та інформатики Чернівецького національного університету імені Юрія Федьковича")))
            .contains(RecognitionLevel.DEPARTMENT);
    }

    @Test
    void ac3_1_aUnitWhoseNameHasATypeWordNeedsItInTheText() {
        assertThat(matcher.level(SuggestionText.of("Алгебра та інформатика"))).isEmpty();
        assertThat(matcher.level(SuggestionText.of("Юридична клініка"))).isEmpty();
        assertThat(matcher.level(SuggestionText.of("Law Society"))).isEmpty();
    }

    @Test
    void ac3_1_codesShorterThanThreeLettersAreNotMatched() {
        assertThat(matcher.level(SuggestionText.of("Florida State College, FL"))).isEmpty();
        assertThat(matcher.level(SuggestionText.of("FMI"))).contains(RecognitionLevel.FACULTY);
    }

    @Test
    void ac3_1_anEmptyOrganisationIsNotLookedUp() {
        assertThat(matcher.level(SuggestionText.of(""))).isEmpty();
        verify(repository, never()).findByActiveTrue();
    }

    private static Organization unit(long id, OrganizationType type, int depth, String name, String nameUk,
                                     String code) {
        return Organization.builder().id(id).orgType(type).depth(depth).name(name).nameUk(nameUk).code(code)
            .active(true).build();
    }
}
