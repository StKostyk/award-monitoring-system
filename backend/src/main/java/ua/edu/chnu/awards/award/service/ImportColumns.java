package ua.edu.chnu.awards.award.service;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.award.dto.ImportField;
import ua.edu.chnu.awards.award.dto.ImportMapping;

/**
 * Guesses which column fills which field from the Ukrainian or English words of the headers. A header that
 * names both the surname and the first name («Прізвище, ім'я, по батькові») is the full name; each field takes
 * the first column that names it and each column fills one field at most; the title is tried last, so «Дата
 * нагородження» or «Award level» is not taken for it.
 */
@Component
public class ImportColumns {

    private static final Map<ImportField, List<String>> WORDS = words();

    /**
     * The guessed mapping.
     *
     * @param headers the header cells
     * @return the mapping, without defaults
     */
    public ImportMapping guess(List<String> headers) {
        Map<ImportField, Integer> columns = new EnumMap<>(ImportField.class);
        for (int column = 0; column < headers.size(); column++) {
            String header = PersonRules.comparable(headers.get(column));
            if (header.isEmpty()) {
                continue;
            }
            ImportField field = field(header);
            if (field != null && !columns.containsKey(field)) {
                columns.put(field, column);
            }
        }
        if (columns.containsKey(ImportField.FULL_NAME)
            && (columns.containsKey(ImportField.LAST_NAME) || columns.containsKey(ImportField.FIRST_NAME))) {
            columns.remove(ImportField.FULL_NAME);
        }
        return new ImportMapping(columns, null, null, null);
    }

    private static ImportField field(String header) {
        boolean last = names(header, ImportField.LAST_NAME);
        boolean first = names(header, ImportField.FIRST_NAME);
        if (last && first || names(header, ImportField.FULL_NAME)) {
            return ImportField.FULL_NAME;
        }
        if (last) {
            return ImportField.LAST_NAME;
        }
        if (first) {
            return ImportField.FIRST_NAME;
        }
        return WORDS.keySet().stream()
            .filter(field -> field != ImportField.FULL_NAME && field != ImportField.LAST_NAME
                && field != ImportField.FIRST_NAME)
            .filter(field -> names(header, field))
            .findFirst()
            .orElse(null);
    }

    private static boolean names(String header, ImportField field) {
        return WORDS.get(field).stream().anyMatch(header::contains);
    }

    private static Map<ImportField, List<String>> words() {
        Map<ImportField, List<String>> words = new LinkedHashMap<>();
        words.put(ImportField.FULL_NAME, List.of("піб", "п.і.б", "п. і. б", "full name", "fullname"));
        words.put(ImportField.LAST_NAME, List.of("прізвище", "surname", "last name", "family name"));
        words.put(ImportField.FIRST_NAME, List.of("імя", "first name", "given name"));
        words.put(ImportField.EMAIL, List.of("e-mail", "email", "пошта", "електронна адреса"));
        words.put(ImportField.DEPARTMENT, List.of("кафедра", "підрозділ", "department"));
        words.put(ImportField.LEVEL, List.of("рівень", "level"));
        words.put(ImportField.DATE, List.of("дата", "date", "рік", "year", "коли"));
        words.put(ImportField.AWARDING_ORGANIZATION, List.of("ким", "видав", "нагороджувач", "організація",
            "awarded by", "issuer", "organization", "organisation"));
        words.put(ImportField.NOTES, List.of("примітк", "опис", "коментар", "notes", "note", "description",
            "comment"));
        words.put(ImportField.TITLE, List.of("нагорода", "назва", "відзнака", "вид нагороди", "award", "title"));
        return words;
    }
}
