package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;

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
    void ac5_1_a_similar_title_is_a_probable_duplicate() {
        ImportDuplicates.Stored stored = new ImportDuplicates.Stored(1L, DAY, "Best Teacher Award 2024", null);

        assertThat(stored.similarTitle("Best Teacher Award")).isTrue();
        assertThat(stored.similarTitle("Honorary Diploma of the City Council")).isFalse();
        assertThat(ImportDuplicates.similar("", "anything")).isFalse();
        assertThat(ImportDuplicates.similar("anything", null)).isFalse();
    }
}
