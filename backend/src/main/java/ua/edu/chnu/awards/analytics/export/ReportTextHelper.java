package ua.edu.chnu.awards.analytics.export;

/**
 * Text rules shared by the spreadsheet formats.
 */
final class ReportTextHelper {

    private static final String FORMULA_STARTS = "=+-@\t\r";

    private ReportTextHelper() {
    }

    /**
     * Keeps a spreadsheet from reading a text cell as a formula.
     *
     * @param text the cell text, or null
     * @return the text, prefixed with an apostrophe when it starts like a formula; empty for null
     */
    static String guard(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return FORMULA_STARTS.indexOf(text.charAt(0)) >= 0 ? "'" + text : text;
    }
}
