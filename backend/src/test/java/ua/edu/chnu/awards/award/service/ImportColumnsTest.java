package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ua.edu.chnu.awards.award.dto.ImportField;
import ua.edu.chnu.awards.award.dto.ImportMapping;

class ImportColumnsTest {

    private final ImportColumns columns = new ImportColumns();

    @Test
    void ac5_1_ukrainian_headers_of_a_faculty_sheet_are_guessed() {
        ImportMapping mapping = columns.guess(List.of("№", "Прізвище, ім'я, по батькові", "Кафедра",
            "Назва нагороди", "Дата нагородження", "Рівень нагороди", "Ким видано", "Примітки"));

        assertThat(mapping.columns()).isEqualTo(Map.of(ImportField.FULL_NAME, 1, ImportField.DEPARTMENT, 2,
            ImportField.TITLE, 3, ImportField.DATE, 4, ImportField.LEVEL, 5, ImportField.AWARDING_ORGANIZATION, 6,
            ImportField.NOTES, 7));
        assertThat(mapping.departmentId()).isNull();
        assertThat(mapping.defaultLevel()).isNull();
    }

    @Test
    void ac5_1_separate_name_columns_and_english_headers_are_guessed() {
        ImportMapping mapping = columns.guess(List.of("Last name", "First name", "E-mail", "Award", "Year",
            "Award level"));

        assertThat(mapping.columns()).isEqualTo(Map.of(ImportField.LAST_NAME, 0, ImportField.FIRST_NAME, 1,
            ImportField.EMAIL, 2, ImportField.TITLE, 3, ImportField.DATE, 4, ImportField.LEVEL, 5));
    }

    @Test
    void ac5_1_the_first_column_naming_a_field_wins_and_unknown_headers_stay_unmapped() {
        ImportMapping mapping = columns.guess(List.of("ПІБ", "Дата", "Дата внесення", "Колір", ""));

        assertThat(mapping.columns()).isEqualTo(Map.of(ImportField.FULL_NAME, 0, ImportField.DATE, 1));
    }
}
