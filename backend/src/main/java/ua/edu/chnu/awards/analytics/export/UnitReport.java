package ua.edu.chnu.awards.analytics.export;

import java.math.BigDecimal;
import java.util.List;

/**
 * The unit analytics laid out as labelled tables, the one model every report format writes.
 *
 * @param language the language of the labels
 * @param title    the report title
 * @param header   unit, period, previous period, production time and producer
 * @param note     the note on the people named and later withdrawals
 * @param empty    the line shown for a section without rows
 * @param columns  the column headings key, label, year, this period and previous period
 * @param sections the sections in report order
 */
public record UnitReport(ReportLanguage language, String title, List<Field> header, String note, String empty,
                         List<String> columns, List<Section> sections) {

    /**
     * Keeps unmodifiable copies of the lists.
     */
    public UnitReport {
        header = List.copyOf(header);
        columns = List.copyOf(columns);
        sections = List.copyOf(sections);
    }

    /**
     * The number of people named in the report.
     *
     * @return the rows of the top performers section
     */
    public long peopleNamed() {
        return sections.stream().filter(section -> Section.TOP_PERFORMERS.equals(section.key()))
            .flatMap(section -> section.rows().stream()).filter(row -> row.current() != null).count();
    }

    /**
     * One line of the report header.
     *
     * @param key   the machine name
     * @param label the label in the report language
     * @param value the value
     */
    public record Field(String key, String label, String value) {
    }

    /**
     * One section of the report.
     *
     * @param key   the machine name
     * @param title the title in the report language
     * @param rows  the rows; empty when the section has nothing to show
     */
    public record Section(String key, String title, List<Row> rows) {

        /** The key of the section that names people. */
        public static final String TOP_PERFORMERS = "topPerformers";

        /**
         * Keeps an unmodifiable copy of the rows.
         */
        public Section {
            rows = List.copyOf(rows);
        }

        /**
         * Whether any row carries a year.
         *
         * @return true when the year column has values
         */
        public boolean hasYears() {
            return rows.stream().anyMatch(row -> row.year() != null);
        }

        /**
         * Whether any row carries a figure of the previous period.
         *
         * @return true when the previous period column has values
         */
        public boolean hasPrevious() {
            return rows.stream().anyMatch(row -> row.previous() != null);
        }
    }

    /**
     * One row of a section.
     *
     * @param key      the machine name, stable across languages
     * @param label    the label in the report language
     * @param year     the calendar year, or null
     * @param current  the figure of the period, or null for a text row
     * @param previous the figure of the previous period, or null
     */
    public record Row(String key, String label, Integer year, BigDecimal current, BigDecimal previous) {

        static Row counts(String key, String label, long current, long previous) {
            return new Row(key, label, null, BigDecimal.valueOf(current), BigDecimal.valueOf(previous));
        }

        static Row count(String key, String label, long current) {
            return new Row(key, label, null, BigDecimal.valueOf(current), null);
        }

        static Row figure(String key, String label, BigDecimal current) {
            return new Row(key, label, null, current, null);
        }

        static Row inYear(String key, String label, int year, long count) {
            return new Row(key, label, year, BigDecimal.valueOf(count), null);
        }

        static Row text(String key, String label) {
            return new Row(key, label, null, null, null);
        }
    }
}
