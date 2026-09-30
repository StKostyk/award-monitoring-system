package ua.edu.chnu.awards.audit.dto;

/**
 * An audit trail written out as a CSV file.
 *
 * @param fileName  attachment name
 * @param content   the file, starting with a byte order mark
 * @param truncated whether older rows were left out because of the row limit
 */
public record AuditTrailExport(String fileName, String content, boolean truncated) {
}
