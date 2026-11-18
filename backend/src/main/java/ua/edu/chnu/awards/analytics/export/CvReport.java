package ua.edu.chnu.awards.analytics.export;

import java.util.List;
import java.util.stream.Stream;

/**
 * The approved awards of one person laid out for a CV, labelled in one language.
 *
 * @param language  the language of the labels
 * @param title     the heading with the name of the person
 * @param generated the line with the date of the export
 * @param years     award years, newest first
 * @param empty     the note shown without awards
 */
public record CvReport(ReportLanguage language, String title, String generated, List<Year> years, String empty) {

    /**
     * Copies the years.
     *
     * @param language  the language of the labels
     * @param title     the heading with the name of the person
     * @param generated the line with the date of the export
     * @param years     award years, newest first
     * @param empty     the note shown without awards
     */
    public CvReport {
        years = List.copyOf(years);
    }

    /**
     * The number of awards listed.
     *
     * @return the count
     */
    public long count() {
        return years.stream().mapToLong(year -> year.entries().size()).sum();
    }

    /**
     * The awards of one year, newest award date first.
     *
     * @param year    the calendar year
     * @param entries the awards
     */
    public record Year(int year, List<Entry> entries) {

        /**
         * Copies the entries.
         *
         * @param year    the calendar year
         * @param entries the awards
         */
        public Year {
            entries = List.copyOf(entries);
        }
    }

    /**
     * One award.
     *
     * @param title  the title in the chosen language when there is one
     * @param level  the recognition level of its category
     * @param issuer the awarding organisation, possibly empty
     * @param date   the award date
     */
    public record Entry(String title, String level, String issuer, String date) {

        /**
         * The level, the issuer when known and the date on one line.
         *
         * @return the line
         */
        public String details() {
            return String.join(" · ", Stream.of(level, issuer, date).filter(part -> part != null && !part.isBlank())
                .toList());
        }
    }
}
