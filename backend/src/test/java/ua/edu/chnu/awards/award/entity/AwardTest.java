package ua.edu.chnu.awards.award.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AwardTest {

    @Test
    void eachLanguageShowsItsOwnTitleWhenBothAreGiven() {
        Award award = Award.builder().title("Certificate").titleUk("Грамота").build();

        assertThat(award.titleInEnglish()).isEqualTo("Certificate");
        assertThat(award.titleInUkrainian()).isEqualTo("Грамота");
    }

    @Test
    void aMissingTitleFallsBackToTheOtherLanguage() {
        assertThat(Award.builder().titleUk("Грамота").build().titleInEnglish()).isEqualTo("Грамота");
        assertThat(Award.builder().title("Certificate").build().titleInUkrainian()).isEqualTo("Certificate");
    }
}
