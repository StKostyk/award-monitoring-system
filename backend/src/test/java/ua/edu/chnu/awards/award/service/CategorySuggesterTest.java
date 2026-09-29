package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.dto.CategorySuggestion;
import ua.edu.chnu.awards.award.dto.SuggestionReason;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.award.repository.CategorySuggestionQueries;
import ua.edu.chnu.awards.award.repository.CategorySuggestionQueries.Candidate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CategorySuggesterTest {

    private static final long CALLER = 21L;

    @Mock
    private CategorySuggestionQueries queries;

    @Mock
    private OrganizationMatcher organizations;

    @Mock
    private AccessScope access;

    @InjectMocks
    private CategorySuggester suggester;

    @BeforeEach
    void setUp() {
        when(access.callerId()).thenReturn(CALLER);
        when(organizations.level(any())).thenReturn(Optional.empty());
        when(queries.mostUsedCategories(CALLER, CategorySuggester.LIMIT)).thenReturn(List.of());
        when(queries.activeCategories()).thenReturn(List.of(
            candidate(1, null, RecognitionLevel.INTERNATIONAL, "international", "міжнародн"),
            candidate(3, 1L, RecognitionLevel.INTERNATIONAL, "best paper", "conferenc"),
            candidate(4, 1L, RecognitionLevel.INTERNATIONAL, "grant"),
            candidate(10, null, RecognitionLevel.NATIONAL, "national", "україн"),
            candidate(13, 10L, RecognitionLevel.NATIONAL, "міністерств"),
            candidate(30, null, RecognitionLevel.FACULTY, "факультет", "faculty"),
            candidate(40, null, RecognitionLevel.DEPARTMENT, "кафедр", "department"),
            candidate(41, 40L, RecognitionLevel.DEPARTMENT, "подяк кафедр")));
    }

    @Test
    void ac3_1_categoryKeywordsTogetherWithLevelKeywordsRankTheSubcategoryFirst() {
        List<CategorySuggestion> found = suggester.suggest("Best paper award", "IEEE International Conference");

        assertThat(found).extracting(CategorySuggestion::id).containsExactly(3L, 1L);
        assertThat(found.get(0).level()).isEqualTo(RecognitionLevel.INTERNATIONAL);
        assertThat(found.get(0).score()).isEqualTo(2 * CategorySuggester.CATEGORY_KEYWORD
            + CategorySuggester.LEVEL_KEYWORD);
        assertThat(found.get(0).reasons()).containsExactly(SuggestionReason.KEYWORD);
        assertThat(found.get(0).nameUk()).isEqualTo("Категорія 3");
    }

    @Test
    void ac3_1_aLevelWordAloneSuggestsTheRootOfItsLevel() {
        List<CategorySuggestion> found = suggester.suggest("Грамота", "Верховна Рада України");

        assertThat(found).extracting(CategorySuggestion::id).containsExactly(10L);
        assertThat(found.get(0).score()).isEqualTo(CategorySuggester.LEVEL_KEYWORD);
    }

    @Test
    void ac3_1_theOrganisationUnitAddsItsLevelToTheRootAndToMatchingSubcategories() {
        when(organizations.level(any())).thenReturn(Optional.of(RecognitionLevel.DEPARTMENT));

        List<CategorySuggestion> found = suggester.suggest("Подяка кафедри", "Кафедра алгебри та інформатики");

        assertThat(found).extracting(CategorySuggestion::id).containsExactly(41L, 40L);
        assertThat(found.get(0).reasons()).containsExactly(SuggestionReason.KEYWORD, SuggestionReason.ORGANISATION);
        assertThat(found.get(0).score()).isEqualTo(CategorySuggester.CATEGORY_KEYWORD
            + CategorySuggester.LEVEL_KEYWORD + CategorySuggester.ORGANISATION);
    }

    @Test
    void ac3_1_anOrganisationUnitWithoutKeywordsStillSuggestsItsLevel() {
        when(organizations.level(any())).thenReturn(Optional.of(RecognitionLevel.FACULTY));

        List<CategorySuggestion> found = suggester.suggest("Подяка", "Юридичний");

        assertThat(found).singleElement().satisfies(suggestion -> {
            assertThat(suggestion.id()).isEqualTo(30L);
            assertThat(suggestion.reasons()).containsExactly(SuggestionReason.ORGANISATION);
        });
    }

    @Test
    void ac3_1_theCallersMostUsedCategoriesComeWithTheLowestWeight() {
        when(queries.mostUsedCategories(CALLER, CategorySuggester.LIMIT)).thenReturn(List.of(13L, 99L, 4L));

        List<CategorySuggestion> found = suggester.suggest("Кафедра", null);

        assertThat(found).extracting(CategorySuggestion::id).containsExactly(40L, 13L, 4L);
        assertThat(found).extracting(CategorySuggestion::score)
            .containsExactly(CategorySuggester.LEVEL_KEYWORD, CategorySuggester.HISTORY,
                CategorySuggester.HISTORY - 2 * CategorySuggester.HISTORY_STEP);
        assertThat(found.get(1).reasons()).containsExactly(SuggestionReason.HISTORY);
        verify(queries).mostUsedCategories(CALLER, CategorySuggester.LIMIT);
    }

    @Test
    void ac3_1_historyAddsToAKeywordMatch() {
        when(queries.mostUsedCategories(CALLER, CategorySuggester.LIMIT)).thenReturn(List.of(13L));

        List<CategorySuggestion> found = suggester.suggest("Грамота Міністерства", null);

        assertThat(found.get(0).id()).isEqualTo(13L);
        assertThat(found.get(0).reasons()).containsExactly(SuggestionReason.KEYWORD, SuggestionReason.HISTORY);
    }

    @Test
    void ac3_1_atMostThreeSuggestionsAndEqualScoresInCatalogueOrder() {
        List<CategorySuggestion> found = suggester.suggest("International national", "Faculty, department");

        assertThat(found).extracting(CategorySuggestion::id).containsExactly(1L, 10L, 30L);
    }

    @Test
    void ac3_1_inputsShorterThanThreeCharactersGiveAnEmptyList() {
        assertThat(suggester.suggest("ab", " x ")).isEmpty();
        assertThat(suggester.suggest(null, null)).isEmpty();
        verifyNoInteractions(queries, organizations);
    }

    @Test
    void ac3_1_aShortTitleIsIgnoredWhenTheOrganisationIsLongEnough() {
        List<CategorySuggestion> found = suggester.suggest("IE", "Міністерство освіти і науки України");

        assertThat(found).extracting(CategorySuggestion::id).containsExactly(13L, 10L);
    }

    @Test
    void ac3_1_aNestedCategoryInheritsTheLevelKeywordsOfItsRootNotItsParent() {
        when(queries.activeCategories()).thenReturn(List.of(
            candidate(1, null, RecognitionLevel.INTERNATIONAL, "international"),
            candidate(3, 1L, RecognitionLevel.INTERNATIONAL, "best paper"),
            candidate(6, 3L, RecognitionLevel.INTERNATIONAL, "workshop"),
            candidate(7, 99L, RecognitionLevel.INTERNATIONAL, "workshop")));

        List<CategorySuggestion> found = suggester.suggest("Best paper, international workshop", null);

        assertThat(found).extracting(CategorySuggestion::id, CategorySuggestion::score).containsExactly(
            tuple(3L, CategorySuggester.CATEGORY_KEYWORD + CategorySuggester.LEVEL_KEYWORD),
            tuple(6L, CategorySuggester.CATEGORY_KEYWORD + CategorySuggester.LEVEL_KEYWORD),
            tuple(1L, CategorySuggester.LEVEL_KEYWORD));
    }

    @Test
    void ac3_1_withoutAnyMatchNothingIsSuggested() {
        assertThat(suggester.suggest("Something else", "Somewhere")).isEmpty();
    }

    private static Candidate candidate(long id, Long parentId, RecognitionLevel level, String... keywords) {
        return new Candidate(id, "Category " + id, "Категорія " + id, level, parentId, List.of(keywords));
    }
}
