package ua.edu.chnu.awards.award.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.award.dto.ImportField;
import ua.edu.chnu.awards.award.dto.ImportProblem;
import ua.edu.chnu.awards.award.entity.AwardCategory;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.award.repository.AwardCategoryRepository;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.entity.OrganizationType;
import ua.edu.chnu.awards.organization.service.OrganizationLookup;

import lombok.RequiredArgsConstructor;

/**
 * The department and the category of an imported row. A department is named by its Ukrainian or English name,
 * with or without the word «кафедра», or by its code, and must lie inside the caller's scope; the category is
 * the active root category of the level.
 */
@Component
@RequiredArgsConstructor
public class ImportUnits {

    private static final Pattern TYPE_WORDS = Pattern.compile("^(кафедра|department of|department)\\s+");

    private final PersonRules rules;
    private final OrganizationLookup organizations;
    private final AwardCategoryRepository categories;

    /**
     * The active departments and root categories, read once per file.
     *
     * @return the lookup
     */
    Lookup load() {
        List<Organization> departments = organizations.findByActiveTrue().stream()
            .filter(unit -> unit.getOrgType() == OrganizationType.DEPARTMENT)
            .toList();
        Map<RecognitionLevel, AwardCategory> roots = new EnumMap<>(RecognitionLevel.class);
        categories.findByActiveTrueOrderBySortOrderAscNameAsc().stream()
            .filter(category -> category.getParent() == null)
            .forEach(category -> roots.putIfAbsent(category.getLevel(), category));
        return new Lookup(departments, roots);
    }

    /**
     * Sets the department of the draft or records why it has none.
     *
     * @param lookup   the lookup
     * @param draft    the draft
     * @param cell     the department cell, empty for none
     * @param fallback the mapping's department, null for none
     */
    void department(Lookup lookup, ImportDraft draft, String cell, Long fallback) {
        if (cell.isEmpty()) {
            if (fallback == null) {
                draft.problem(ImportProblem.blocking("department-missing", ImportField.DEPARTMENT));
            } else {
                lookup.departments().stream().filter(unit -> unit.getId().equals(fallback)).findFirst()
                    .ifPresent(draft::setDepartment);
            }
            return;
        }
        String wanted = plain(cell);
        List<Organization> named = lookup.departments().stream()
            .filter(unit -> wanted.equals(plain(unit.getNameUk())) || wanted.equals(plain(unit.getName()))
                || unit.getCode() != null && wanted.equals(PersonRules.comparable(unit.getCode())))
            .toList();
        if (named.size() != 1) {
            draft.problem(ImportProblem.blocking("department-unknown", ImportField.DEPARTMENT));
        } else if (rules.covers(named.get(0).getId())) {
            draft.setDepartment(named.get(0));
        } else {
            draft.problem(ImportProblem.blocking("department-out-of-scope", ImportField.DEPARTMENT));
        }
    }

    /**
     * Sets the category of the level or records why it has none.
     *
     * @param lookup the lookup
     * @param draft  the draft
     * @param level  the level, empty when the cell named none
     * @param code   the problem when there is no level
     */
    void category(Lookup lookup, ImportDraft draft, Optional<RecognitionLevel> level, String code) {
        AwardCategory category = level.map(lookup.roots()::get).orElse(null);
        if (category == null) {
            draft.problem(ImportProblem.blocking(level.isEmpty() ? code : "level-unknown", ImportField.LEVEL));
        }
        draft.setCategory(category);
    }

    private static String plain(String name) {
        return name == null ? "" : TYPE_WORDS.matcher(PersonRules.comparable(name)).replaceFirst("");
    }

    /**
     * The departments and categories of one file.
     *
     * @param departments the active departments
     * @param roots       the active root category of each level
     */
    record Lookup(List<Organization> departments, Map<RecognitionLevel, AwardCategory> roots) {
    }
}
