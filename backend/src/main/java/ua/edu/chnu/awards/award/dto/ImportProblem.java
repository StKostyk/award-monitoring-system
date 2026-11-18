package ua.edu.chnu.awards.award.dto;

/**
 * A problem of one row of an imported spreadsheet.
 *
 * @param code     the problem, e.g. {@code date-future} or {@code duplicate-probable}
 * @param field    the field it concerns, null for the whole row
 * @param blocking true when the file cannot be imported until the row is fixed
 */
public record ImportProblem(String code, ImportField field, boolean blocking) {

    /**
     * A problem that blocks the import.
     *
     * @param code  the problem
     * @param field the field, null for the whole row
     * @return the problem
     */
    public static ImportProblem blocking(String code, ImportField field) {
        return new ImportProblem(code, field, true);
    }

    /**
     * A notice that does not block the import.
     *
     * @param code  the notice
     * @param field the field, null for the whole row
     * @return the notice
     */
    public static ImportProblem notice(String code, ImportField field) {
        return new ImportProblem(code, field, false);
    }
}
