package ua.edu.chnu.awards.analytics.export;

import java.util.List;

/**
 * The university analytics and the compliance summary laid out as the pages of the board report.
 *
 * @param language the language of the labels
 * @param title    the report title
 * @param header   university, periods, production time, producer and data time
 * @param note     the note that nobody is named
 * @param pages    one page per topic, in report order
 */
public record BoardReport(ReportLanguage language, String title, List<UnitReport.Field> header, String note,
                          List<Page> pages) {

    /**
     * Keeps unmodifiable copies of the lists.
     */
    public BoardReport {
        header = List.copyOf(header);
        pages = List.copyOf(pages);
    }

    /**
     * One topic of the report.
     *
     * @param key    the machine name
     * @param title  the title in the report language
     * @param notes  sentences shown under the title
     * @param tables the tables
     * @param charts the bar charts
     */
    public record Page(String key, String title, List<String> notes, List<Table> tables, List<Chart> charts) {

        /**
         * Keeps unmodifiable copies of the lists.
         */
        public Page {
            notes = List.copyOf(notes);
            tables = List.copyOf(tables);
            charts = List.copyOf(charts);
        }
    }

    /**
     * A table with a heading row.
     *
     * @param title   the caption, or null
     * @param columns the column headings, the item column first
     * @param rows    the rows
     * @param empty   the line shown when there are no rows
     */
    public record Table(String title, List<String> columns, List<Row> rows, String empty) {

        /**
         * Keeps unmodifiable copies of the lists.
         */
        public Table {
            columns = List.copyOf(columns);
            rows = List.copyOf(rows);
        }
    }

    /**
     * One row of a table.
     *
     * @param label  the item
     * @param values the values of the other columns, already formatted
     */
    public record Row(String label, List<String> values) {

        /**
         * Keeps an unmodifiable copy of the values.
         */
        public Row {
            values = List.copyOf(values);
        }
    }

    /**
     * Horizontal bars drawn by the template.
     *
     * @param title the caption
     * @param bars  the bars
     */
    public record Chart(String title, List<Bar> bars) {

        /**
         * Keeps an unmodifiable copy of the bars.
         */
        public Chart {
            bars = List.copyOf(bars);
        }
    }

    /**
     * One bar.
     *
     * @param label the item
     * @param value the value as printed beside the bar
     * @param width the length in percent of the largest value
     */
    public record Bar(String label, String value, int width) {
    }
}
