package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class ImportDuplicatesTest {

    private static final LocalDate DAY = LocalDate.of(2024, 5, 17);

    @Test
    void ac5_2_the_same_title_ignores_case_spaces_and_punctuation() {
        ImportDuplicates.Stored stored = new ImportDuplicates.Stored(1L, DAY, null, "Грамота МОН України");

        assertThat(stored.sameTitle("грамота  МОН України.")).isTrue();
        assertThat(stored.sameTitle("Грамота МОН")).isFalse();
    }

    @Test
    void ac2_a_decomposed_title_is_the_same_as_the_composed_one() {
        String title = "Подяка за їжу й воду";
        String decomposed = Normalizer.normalize(title, Normalizer.Form.NFD);
        ImportDuplicates.Stored stored = new ImportDuplicates.Stored(1L, DAY, null, title);
        ImportDuplicates.Stored storedDecomposed = new ImportDuplicates.Stored(1L, DAY, null, decomposed);

        assertThat(stored.sameTitle(decomposed)).isTrue();
        assertThat(storedDecomposed.sameTitle(title)).isTrue();
        assertThat(ImportDuplicates.plainTitle(decomposed)).isEqualTo("подяка за їжу й воду");
    }

    @Test
    void ac5_1_a_similar_title_is_a_probable_duplicate() {
        ImportDuplicates.Stored stored = new ImportDuplicates.Stored(1L, DAY, "Best Teacher Award 2024", null);

        assertThat(stored.similarTitle("Best Teacher Award")).isTrue();
        assertThat(stored.similarTitle("Honorary Diploma of the City Council")).isFalse();
        assertThat(ImportDuplicates.similar("", "anything")).isFalse();
        assertThat(ImportDuplicates.similar("anything", null)).isFalse();
    }
}
