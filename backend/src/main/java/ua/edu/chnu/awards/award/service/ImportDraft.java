package ua.edu.chnu.awards.award.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import ua.edu.chnu.awards.award.dto.CategoryRef;
import ua.edu.chnu.awards.award.dto.ImportPerson;
import ua.edu.chnu.awards.award.dto.ImportProblem;
import ua.edu.chnu.awards.award.dto.ImportRow;
import ua.edu.chnu.awards.award.dto.UnitRef;
import ua.edu.chnu.awards.award.entity.AwardCategory;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.user.entity.User;

import lombok.Getter;
import lombok.Setter;

/**
 * A spreadsheet row while it is being read and matched.
 */
@Getter
@Setter
class ImportDraft {

    private final int number;
    private final List<ImportProblem> problems = new ArrayList<>();
    private ImportValues.ImportName name;
    private String email = "";
    private Organization department;
    private User owner;
    private String title;
    private LocalDate awardDate;
    private String awardingOrganization;
    private String notes;
    private AwardCategory category;

    ImportDraft(int number) {
        this.number = number;
    }

    void problem(ImportProblem problem) {
        problems.add(problem);
    }

    Optional<ImportValues.ImportName> name() {
        return Optional.ofNullable(name);
    }

    boolean comparable() {
        return title != null && awardDate != null && (owner != null || name != null && department != null);
    }

    String key() {
        String person = owner != null ? "id:" + owner.getId() : "new:" + department.getId() + ':'
            + PersonRules.comparable(name.lastName()) + ':' + PersonRules.comparable(name.firstName());
        return person + '|' + awardDate + '|' + ImportDuplicates.plainTitle(title);
    }

    ImportRow row(boolean skipped) {
        ImportPerson person = owner != null ? ImportPerson.of(owner)
            : name().filter(found -> department != null && !found.initial())
                .map(found -> new ImportPerson(null, found.firstName(), found.lastName(), true))
                .orElse(null);
        CategoryRef categoryRef = category == null ? null
            : new CategoryRef(category.getId(), category.getName(), category.getNameUk(), category.getLevel());
        return new ImportRow(number, person, department == null ? null : UnitRef.of(department), title, awardDate,
            awardingOrganization, notes, categoryRef, skipped, problems);
    }
}
