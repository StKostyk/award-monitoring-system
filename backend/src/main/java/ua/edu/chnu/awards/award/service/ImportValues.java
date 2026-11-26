package ua.edu.chnu.awards.award.service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.award.dto.ImportField;
import ua.edu.chnu.awards.award.dto.ImportProblem;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.user.dto.NameConstants;

import lombok.RequiredArgsConstructor;

/**
 * Reads the names, dates and levels of a spreadsheet row. A full name is «Прізвище Ім'я [По батькові]», the
 * first name possibly as an initial («Петренко І. П.»); a date is {@code yyyy-MM-dd}, {@code dd.MM.yyyy} (also
 * with {@code /} or {@code -}), a month {@code MM.yyyy} (its first day), a year (1 January) or an Excel day
 * number; a level is read from the stems of its Ukrainian or English name («всеукраїнський», «обласна»).
 */
@Component
@RequiredArgsConstructor
public class ImportValues {

    private static final int NAME_MAX = 100;
    private static final int EXCEL_FIRST = 20_000;
    private static final int EXCEL_LAST = 80_000;
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);
    private static final Pattern NAME = Pattern.compile(NameConstants.NAME);
    private static final Pattern INITIALS = Pattern.compile("^(\\p{L})\\.(?:\\s*\\p{L}\\.)?$");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s,]+");
    private static final Pattern ISO =
        Pattern.compile("^(?<year>\\d{4})-(?<month>\\d{1,2})-(?<day>\\d{1,2})(?:[ T].*)?$");
    private static final Pattern DAY_FIRST =
        Pattern.compile("^(?<day>\\d{1,2})[./-](?<month>\\d{1,2})[./-](?<year>\\d{4})(?:\\s.*)?$");
    private static final Pattern MONTH = Pattern.compile("^(?<month>\\d{1,2})[./-](?<year>\\d{4})$");
    private static final Pattern YEAR = Pattern.compile("^(?<year>\\d{4})(?:\\s*р\\.?)?$");
    private static final Pattern DAY_NUMBER = Pattern.compile("^\\d{5}$");
    private static final Map<String, RecognitionLevel> LEVELS = levels();

    private final AwardDateRules dates;

    /**
     * The name of a row.
     *
     * @param fullName  the full-name cell, empty when not mapped
     * @param lastName  the surname cell, empty when not mapped
     * @param firstName the first-name cell, empty when not mapped
     * @return the name, empty when it is missing or not a name
     */
    public Optional<ImportName> name(String fullName, String lastName, String firstName) {
        String last;
        String first;
        if (fullName.isBlank()) {
            last = lastName.strip();
            first = firstName.strip();
        } else {
            List<String> words = List.of(SEPARATORS.split(fullName.strip()));
            if (words.size() < 2) {
                return Optional.empty();
            }
            last = words.get(0);
            first = initialsOrWord(words);
        }
        if (!isName(last)) {
            return Optional.empty();
        }
        Matcher initial = INITIALS.matcher(first);
        if (initial.matches()) {
            return Optional.of(new ImportName(last, initial.group(1), true));
        }
        return isName(first) ? Optional.of(new ImportName(last, first, false)) : Optional.empty();
    }

    /**
     * The date of a cell.
     *
     * @param cell the cell text
     * @return the date, empty when the text is not a date
     */
    public Optional<LocalDate> date(String cell) {
        String text = cell.strip();
        try {
            for (Pattern pattern : List.of(ISO, DAY_FIRST, MONTH, YEAR)) {
                Matcher matcher = pattern.matcher(text);
                if (matcher.matches()) {
                    return Optional.of(LocalDate.of(number(matcher, "year"), part(matcher, "month"),
                        part(matcher, "day")));
                }
            }
        } catch (DateTimeException exception) {
            return Optional.empty();
        }
        if (DAY_NUMBER.matcher(text).matches()) {
            int days = Integer.parseInt(text);
            return days >= EXCEL_FIRST && days <= EXCEL_LAST ? Optional.of(EXCEL_EPOCH.plusDays(days))
                : Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * Sets the date of the draft or records why it has none: missing, unreadable, in the future or too old.
     *
     * @param draft the draft
     * @param cell  the date cell
     */
    void date(ImportDraft draft, String cell) {
        if (cell.isEmpty()) {
            draft.problem(ImportProblem.blocking("date-missing", ImportField.DATE));
            return;
        }
        LocalDate date = date(cell).orElse(null);
        if (date == null) {
            draft.problem(ImportProblem.blocking("date-invalid", ImportField.DATE));
            return;
        }
        dates.check(date).ifPresentOrElse(
            refusal -> draft.problem(ImportProblem.blocking("date-" + refusal.code(), ImportField.DATE)),
            () -> draft.setAwardDate(date));
    }

    /**
     * The level of a cell.
     *
     * @param cell the cell text
     * @return the level, empty when no level is named
     */
    public Optional<RecognitionLevel> level(String cell) {
        String text = cell.strip().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return Optional.empty();
        }
        return LEVELS.entrySet().stream()
            .filter(entry -> text.contains(entry.getKey()))
            .map(Map.Entry::getValue)
            .findFirst();
    }

    private static String initialsOrWord(List<String> words) {
        String second = words.get(1);
        if (words.size() > 2 && second.matches("^\\p{L}\\.$") && words.get(2).matches("^\\p{L}\\.$")) {
            return second + words.get(2);
        }
        return second;
    }

    private static boolean isName(String value) {
        return !value.isEmpty() && value.length() <= NAME_MAX && NAME.matcher(value).matches();
    }

    private static int number(Matcher matcher, String group) {
        return Integer.parseInt(matcher.group(group));
    }

    private static int part(Matcher matcher, String group) {
        return matcher.namedGroups().containsKey(group) ? number(matcher, group) : 1;
    }

    private static Map<String, RecognitionLevel> levels() {
        List<Map.Entry<RecognitionLevel, List<String>>> stems = List.of(
            Map.entry(RecognitionLevel.INTERNATIONAL, List.of("міжнарод", "international")),
            Map.entry(RecognitionLevel.NATIONAL, List.of("всеукраїн", "національ", "державн", "national")),
            Map.entry(RecognitionLevel.REGIONAL, List.of("регіональ", "обласн", "regional")),
            Map.entry(RecognitionLevel.LOCAL, List.of("місцев", "міськ", "local", "city")),
            Map.entry(RecognitionLevel.UNIVERSITY, List.of("університет", "university")),
            Map.entry(RecognitionLevel.FACULTY, List.of("факультет", "faculty")),
            Map.entry(RecognitionLevel.COLLEGE, List.of("коледж", "college")),
            Map.entry(RecognitionLevel.DEPARTMENT, List.of("кафедр", "department")),
            Map.entry(RecognitionLevel.SPECIALITY, List.of("спеціальн", "speciality", "specialty")));
        Map<String, RecognitionLevel> levels = new LinkedHashMap<>();
        stems.forEach(entry -> entry.getValue().forEach(stem -> levels.put(stem, entry.getKey())));
        return levels;
    }

    /**
     * A name read from a row.
     *
     * @param lastName  surname
     * @param firstName first name, or its initial letter
     * @param initial   true when only the initial of the first name is known
     */
    public record ImportName(String lastName, String firstName, boolean initial) {
    }
}
