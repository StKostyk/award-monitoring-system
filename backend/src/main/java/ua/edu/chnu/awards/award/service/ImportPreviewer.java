package ua.edu.chnu.awards.award.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.award.dto.ImportField;
import ua.edu.chnu.awards.award.dto.ImportMapping;
import ua.edu.chnu.awards.award.dto.ImportProblem;
import ua.edu.chnu.awards.award.dto.ImportRow;
import ua.edu.chnu.awards.user.entity.User;

import lombok.RequiredArgsConstructor;

/**
 * Turns the lines of a spreadsheet into import rows: the department, the level and the awarding organisation
 * (the row's, or the mapping's for an empty cell), the person, the root category of the level, and the problems.
 * A row whose award is stored already, or that repeats an earlier row, is left out.
 */
@Component
@RequiredArgsConstructor
public class ImportPreviewer {

    private static final int TITLE_MAX = 500;
    private static final int ORGANIZATION_MAX = 255;
    private static final int NOTES_MAX = 4000;
    private static final Pattern CYRILLIC = Pattern.compile(".*\\p{IsCyrillic}.*", Pattern.DOTALL);

    private final ImportValues values;
    private final ImportUnits units;
    private final ImportPeople people;
    private final ImportDuplicates duplicates;

    /**
     * The rows of the sheet.
     *
     * @param sheet   the sheet
     * @param mapping the mapping
     * @return the rows in sheet order
     */
    public List<ImportRow> rows(ImportSheet sheet, ImportMapping mapping) {
        ImportUnits.Lookup lookup = units.load();
        List<ImportDraft> drafts = sheet.rows().stream().map(line -> draft(line, mapping, lookup)).toList();
        people.match(drafts);
        return finish(drafts);
    }

    /**
     * Whether a text is Ukrainian, stored in the Ukrainian field.
     *
     * @param text the text
     * @return true when it has a Cyrillic letter
     */
    public static boolean isUkrainian(String text) {
        return CYRILLIC.matcher(text).matches();
    }

    private ImportDraft draft(ImportSheet.Line line, ImportMapping mapping, ImportUnits.Lookup lookup) {
        Map<ImportField, Integer> columns = mapping.columns();
        ImportDraft draft = new ImportDraft(line.number());
        draft.setEmail(line.cell(columns.get(ImportField.EMAIL)));
        String fullName = line.cell(columns.get(ImportField.FULL_NAME));
        String lastName = line.cell(columns.get(ImportField.LAST_NAME));
        draft.setName(values.name(fullName, lastName, line.cell(columns.get(ImportField.FIRST_NAME))).orElse(null));
        if (draft.getName() == null) {
            draft.problem(ImportProblem.blocking(fullName.isEmpty() && lastName.isEmpty() ? "name-missing"
                : "name-invalid", ImportField.FULL_NAME));
        }
        units.department(lookup, draft, line.cell(columns.get(ImportField.DEPARTMENT)), mapping.departmentId());
        draft.setTitle(text(draft, line.cell(columns.get(ImportField.TITLE)), ImportField.TITLE, TITLE_MAX));
        if (draft.getTitle() == null) {
            draft.problem(ImportProblem.blocking("title-missing", ImportField.TITLE));
        }
        awardedBy(draft, line.cell(columns.get(ImportField.AWARDING_ORGANIZATION)),
            mapping.defaultAwardingOrganization());
        draft.setNotes(text(draft, line.cell(columns.get(ImportField.NOTES)), ImportField.NOTES, NOTES_MAX));
        values.date(draft, line.cell(columns.get(ImportField.DATE)));
        String level = line.cell(columns.get(ImportField.LEVEL));
        units.category(lookup, draft, level.isEmpty() ? Optional.ofNullable(mapping.defaultLevel())
            : values.level(level), level.isEmpty() ? "level-missing" : "level-unknown");
        return draft;
    }

    private static void awardedBy(ImportDraft draft, String cell, String fallback) {
        draft.setAwardingOrganization(text(draft, cell.isEmpty() ? fallback : cell, ImportField.AWARDING_ORGANIZATION,
            ORGANIZATION_MAX));
        if (draft.getAwardingOrganization() == null) {
            draft.problem(ImportProblem.blocking("awarding-organization-missing", ImportField.AWARDING_ORGANIZATION));
        }
    }

    private List<ImportRow> finish(List<ImportDraft> drafts) {
        Set<Long> owners = drafts.stream().map(ImportDraft::getOwner).filter(Objects::nonNull)
            .map(User::getId).collect(Collectors.toSet());
        Set<LocalDate> days = drafts.stream().map(ImportDraft::getAwardDate).filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, List<ImportDuplicates.Stored>> stored = duplicates.stored(owners, days).stream()
            .collect(Collectors.groupingBy(ImportDuplicates.Stored::ownerId));
        Set<String> seen = new HashSet<>();
        List<ImportRow> rows = new ArrayList<>();
        for (ImportDraft draft : drafts) {
            boolean skipped = false;
            if (draft.comparable()) {
                skipped = !seen.add(draft.key());
                if (skipped) {
                    draft.problem(ImportProblem.notice("row-repeated", null));
                } else if (draft.getOwner() != null) {
                    skipped = compare(draft, stored.getOrDefault(draft.getOwner().getId(), List.of()));
                }
            }
            rows.add(draft.row(skipped));
        }
        return rows;
    }

    private static boolean compare(ImportDraft draft, List<ImportDuplicates.Stored> stored) {
        List<ImportDuplicates.Stored> sameDay = stored.stream()
            .filter(award -> award.awardDate().equals(draft.getAwardDate()))
            .toList();
        if (sameDay.stream().anyMatch(award -> award.sameTitle(draft.getTitle()))) {
            draft.problem(ImportProblem.notice("already-stored", null));
            return true;
        }
        if (sameDay.stream().anyMatch(award -> award.similarTitle(draft.getTitle()))) {
            draft.problem(ImportProblem.notice("duplicate-probable", ImportField.TITLE));
        }
        return false;
    }

    private static String text(ImportDraft draft, String cell, ImportField field, int max) {
        if (cell.isEmpty()) {
            return null;
        }
        if (cell.length() > max) {
            draft.problem(ImportProblem.blocking(field.name().toLowerCase(Locale.ROOT).replace('_', '-')
                + "-too-long", field));
        }
        return cell;
    }
}
