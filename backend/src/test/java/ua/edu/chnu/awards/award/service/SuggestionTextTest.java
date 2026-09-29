package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SuggestionTextTest {

    @Test
    void ac3_1_aKeywordStemMatchesTheStartOfAWordIgnoringCase() {
        SuggestionText text = SuggestionText.of("Грамота МІНІСТЕРСТВА освіти і науки");

        assertThat(text.contains("міністерств")).isTrue();
        assertThat(text.contains("Освіт")).isTrue();
        assertThat(text.contains("ніст")).isFalse();
    }

    @Test
    void ac3_1_aKeywordOfSeveralStemsMatchesConsecutiveWordsOnly() {
        SuggestionText text = SuggestionText.of("Подяка міського голови, м. Чернівці");

        assertThat(text.contains("міськ голов")).isTrue();
        assertThat(text.contains("голов міськ")).isFalse();
        assertThat(text.contains("подяк голов")).isFalse();
    }

    @Test
    void ac3_1_nationalDoesNotMatchInsideInternational() {
        SuggestionText text = SuggestionText.of("IEEE International Conference");

        assertThat(text.contains("national")).isFalse();
        assertThat(text.contains("international")).isTrue();
    }

    @Test
    void ac3_1_apostrophesAndPunctuationAreIgnored() {
        SuggestionText text = SuggestionText.of("Спеціальність «Комп’ютерні науки»");

        assertThat(text.contains("компʼютерн")).isTrue();
        assertThat(text.contains("комп'ютерн наук")).isTrue();
        assertThat(text.words()).containsExactly("спеціальність", "компютерні", "науки");
    }

    @Test
    void ac3_1_emptyTextOrKeywordMatchesNothing() {
        assertThat(SuggestionText.of((String) null).isEmpty()).isTrue();
        assertThat(SuggestionText.of(" — ").isEmpty()).isTrue();
        assertThat(SuggestionText.of("Подяка").contains(" ")).isFalse();
        assertThat(SuggestionText.of("Подяка").isEmpty()).isFalse();
    }

    @Test
    void ac3_1_theStemOfANameWordDropsItsEnding() {
        assertThat(SuggestionText.stem("Кафедра")).isEqualTo("кафед");
        assertThat(SuggestionText.stem("інформатики")).isEqualTo("інформати");
        assertThat(SuggestionText.stem("Мова")).isEqualTo("мова");
        assertThat(SuggestionText.stem("хімії")).isEqualTo("хімі");
    }

    @Test
    void ac3_1_textJoinsItsPartsWithoutMergingWords() {
        SuggestionText text = SuggestionText.of("Подяка", null, "кафедри");

        assertThat(text.words()).containsExactly("подяка", "кафедри");
        assertThat(text.contains("подяк кафедр")).isFalse();
    }
}
