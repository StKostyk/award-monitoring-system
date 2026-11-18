package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import ua.edu.chnu.awards.award.dto.ImportProblem;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;

class ImportValuesTest {

    private final ImportValues values = new ImportValues(new AwardDateRules(
        Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneId.of("Europe/Kyiv"))));

    @Test
    void ac5_1_full_name_is_surname_then_first_name_and_drops_the_patronymic() {
        assertThat(values.name("Коваль Анастасія Петрівна", "", "")).hasValue(
            new ImportValues.ImportName("Коваль", "Анастасія", false));
        assertThat(values.name("Коваль, Анастасія", "", "")).hasValue(
            new ImportValues.ImportName("Коваль", "Анастасія", false));
    }

    @Test
    void ac5_1_initials_keep_only_the_first_letter() {
        assertThat(values.name("Петренко І.П.", "", "")).hasValue(new ImportValues.ImportName("Петренко", "І", true));
        assertThat(values.name("Петренко І. П.", "", "")).hasValue(
            new ImportValues.ImportName("Петренко", "І", true));
    }

    @Test
    void ac5_1_separate_columns_and_an_apostrophe_are_read() {
        String first = Normalizer.normalize("Мар'яна", Normalizer.Form.NFC);
        assertThat(values.name("", "Ковальчук", first)).hasValue(new ImportValues.ImportName("Ковальчук", first,
            false));
    }

    @Test
    void ac5_1_a_single_word_or_digits_are_not_a_name() {
        assertThat(values.name("Коваль", "", "")).isEmpty();
        assertThat(values.name("Коваль 123", "", "")).isEmpty();
        assertThat(values.name("", "", "")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "2024-05-17, 2024-05-17",
        "2024-05-17 00:00:00, 2024-05-17",
        "17.05.2024, 2024-05-17",
        "7/5/2024, 2024-05-07",
        "05.2024, 2024-05-01",
        "2024, 2024-01-01",
        "2024 р., 2024-01-01",
        "45429, 2024-05-17"
    })
    void ac5_1_dates_are_read_in_the_usual_formats(String cell, LocalDate expected) {
        assertThat(values.date(cell)).hasValue(expected);
    }

    @ParameterizedTest
    @CsvSource({"31.02.2024", "травень 2024", "12345", "2024-13-01"})
    void ac5_1_text_that_is_no_date_is_refused(String cell) {
        assertThat(values.date(cell)).isEmpty();
    }

    @Test
    void ac5_1_a_future_or_too_old_date_blocks_the_row() {
        ImportDraft future = new ImportDraft(2);
        values.date(future, "01.01.2027");
        ImportDraft old = new ImportDraft(3);
        values.date(old, "1970");
        ImportDraft fine = new ImportDraft(4);
        values.date(fine, "2026");
        assertThat(future.getProblems()).extracting(ImportProblem::code).containsExactly("date-future");
        assertThat(old.getProblems()).extracting(ImportProblem::code).containsExactly("date-too-old");
        assertThat(fine.getProblems()).isEmpty();
        assertThat(fine.getAwardDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @ParameterizedTest
    @CsvSource({
        "Міжнародний, INTERNATIONAL",
        "всеукраїнська, NATIONAL",
        "Державна нагорода, NATIONAL",
        "обласний, REGIONAL",
        "міська, LOCAL",
        "Університетський, UNIVERSITY",
        "факультетський, FACULTY",
        "кафедральний, DEPARTMENT",
        "International, INTERNATIONAL",
        "national, NATIONAL"
    })
    void ac5_1_levels_are_read_from_their_stems(String cell, RecognitionLevel expected) {
        assertThat(values.level(cell)).hasValue(expected);
    }

    @Test
    void ac5_1_an_unknown_level_is_empty() {
        assertThat(values.level("особливий")).isEmpty();
        assertThat(values.level(" ")).isEmpty();
    }
}
