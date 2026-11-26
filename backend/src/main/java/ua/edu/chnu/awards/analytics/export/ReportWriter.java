package ua.edu.chnu.awards.analytics.export;

/**
 * Writes a unit report in one file format.
 */
public interface ReportWriter {

    /**
     * The format this writer produces.
     *
     * @return the format
     */
    ReportFormat format();

    /**
     * Writes the report.
     *
     * @param report the report
     * @return the file content
     */
    byte[] write(UnitReport report);
}
