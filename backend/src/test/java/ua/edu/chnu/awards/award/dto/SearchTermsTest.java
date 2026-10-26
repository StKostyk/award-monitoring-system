package ua.edu.chnu.awards.award.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.common.web.ApiProblemException;

class SearchTermsTest {

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "a", "  п  "})
    void ac1_1_aMissingOrShortTextIsNotSearched(String text) {
        assertThat(SearchTerms.of(text)).isNull();
        assertThat(SearchTerms.of(null)).isNull();
    }

    @ParameterizedTest
    @CsvSource({
        "премія, прем",
        "премії, прем",
        "Шевченко, шевченк",
        "award, award",
        "пʼять, пять",
        "грамоти, грамот",
        "студентів, студентів",
        "мова, мова",
        "україна, україн"
    })
    void ac1_3_wordsAreLowerCasedAndStemmed(String text, String stem) {
        assertThat(SearchTerms.of(text).tsquery()).isEqualTo(stem + ":*");
    }

    @Test
    void ac1_3_theThreeApostrophesAreRemovedAlike() {
        assertThat(SearchTerms.of("п'ять").words()).containsExactly("пять");
        assertThat(SearchTerms.of("п’ять").words()).containsExactly("пять");
        assertThat(SearchTerms.of("пʼять").words()).containsExactly("пять");
    }

    @Test
    void ac1_3_everyWordIsRequiredAsAPrefix() {
        SearchTerms terms = SearchTerms.of("  Best   pap ");

        assertThat(terms.tsquery()).isEqualTo("best:* & pap:*");
        assertThat(terms.trigram()).isEqualTo("best pap");
        assertThat(terms.text()).isEqualTo("Best   pap");
    }

    @Test
    void ac1_7_atMostTenWordsAreUsed() {
        assertThat(SearchTerms.of("a1 b2 c3 d4 e5 f6 g7 h8 i9 j10 k11 l12").words())
            .hasSize(SearchTerms.MAX_WORDS).endsWith("j10");
    }

    @Test
    void ac1_7_aTextOver200CharactersIsRefused() {
        assertThat(SearchTerms.of("x".repeat(SearchTerms.MAX_LENGTH))).isNotNull();
        assertThatThrownBy(() -> SearchTerms.of("x".repeat(SearchTerms.MAX_LENGTH + 1)))
            .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(problem.getProperties()).isEqualTo(Map.of("parameter", "q"));
            });
    }

    @ParameterizedTest
    @ValueSource(strings = {"a & b | !c:*", "(a) <-> b", "'; drop --", "a\\b", "50%_x"})
    void ac1_7_querySyntaxSeparatesWords(String text) {
        assertThat(SearchTerms.of(text).tsquery()).matches("[\\p{L}\\p{N}]+:\\*( & [\\p{L}\\p{N}]+:\\*)*");
    }

    @Test
    void ac1_7_aTextWithoutLettersOrDigitsIsEmpty() {
        assertThat(SearchTerms.of("??").isEmpty()).isTrue();
        assertThat(SearchTerms.of("-- %").isEmpty()).isTrue();
    }

    @Test
    void ac1_6_theNamePatternEscapesWildcards() {
        assertThat(SearchTerms.of("50%_a\\b").namePattern()).isEqualTo("%50\\%\\_a\\\\b%");
    }
}
