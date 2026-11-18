package ua.edu.chnu.awards.award.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * One row of an imported spreadsheet as it would be stored.
 *
 * @param row        the row number in the spreadsheet, the header being row 1
 * @param person     the matched or added person, null when the name could not be read
 * @param department the department, null when unknown
 * @param title      the award title
 * @param awardDate  the award date, null when unreadable
 * @param awardingOrganization who granted the award, null for none
 * @param notes      the notes, stored as the description, null for none
 * @param category   the category of the level, null when unknown
 * @param skipped    true when the award is stored already and the import leaves the row out
 * @param problems   the problems and notices
 */
@SuppressWarnings("PMD.CommentSize")
public record ImportRow(int row, ImportPerson person, UnitRef department, String title, LocalDate awardDate,
                        String awardingOrganization, String notes, CategoryRef category, boolean skipped,
                        List<ImportProblem> problems) {

    /**
     * Copies the problems.
     *
     * @param row        the row number
     * @param person     the person
     * @param department the department
     * @param title      the title
     * @param awardDate  the date
     * @param awardingOrganization who granted it
     * @param notes      the notes
     * @param category   the category
     * @param skipped    whether the row is left out
     * @param problems   the problems
     */
    public ImportRow {
        problems = List.copyOf(problems);
    }

    /**
     * Whether a problem blocks the import.
     *
     * @return true with a blocking problem
     */
    public boolean blocked() {
        return problems.stream().anyMatch(ImportProblem::blocking);
    }
}
