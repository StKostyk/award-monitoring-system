package ua.edu.chnu.awards.gdpr.dto;

/**
 * A finished export ready for download.
 *
 * @param fileName the attachment name, dated with the export day
 * @param content  the exported data
 */
public record DataExport(String fileName, PersonalDataFile content) {
}
