package ua.edu.chnu.awards.award.service;

import java.text.Normalizer;
import java.util.List;

/**
 * The cells of an uploaded spreadsheet as text: the header row and the non-empty rows below it, in Unicode
 * normalisation form C, so that a file saved with decomposed letters matches the stored people and awards.
 *
 * @param headers the header cells
 * @param rows    the rows below the header that have at least one cell
 */
public record ImportSheet(List<String> headers, List<Line> rows) {

    /**
     * Copies the lists, the headers in form C.
     *
     * @param headers the header cells
     * @param rows    the rows
     */
    public ImportSheet {
        headers = List.copyOf(composed(headers));
        rows = List.copyOf(rows);
    }

    private static List<String> composed(List<String> texts) {
        return texts.stream().map(text -> Normalizer.normalize(text, Normalizer.Form.NFC)).toList();
    }

    /**
     * One row of the spreadsheet.
     *
     * @param number the row number, the header being row 1
     * @param cells  the cells as text, empty text for an empty cell
     */
    public record Line(int number, List<String> cells) {

        /**
         * Copies the cells in form C.
         *
         * @param number the row number
         * @param cells  the cells
         */
        public Line {
            cells = List.copyOf(composed(cells));
        }

        /**
         * The trimmed text of a column.
         *
         * @param column the 0-based column, null for an unmapped field
         * @return the text, empty when the column is unmapped, missing or empty
         */
        public String cell(Integer column) {
            return column == null || column < 0 || column >= cells.size() ? "" : cells.get(column).strip();
        }
    }
}
