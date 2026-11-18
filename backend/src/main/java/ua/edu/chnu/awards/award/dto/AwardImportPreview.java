package ua.edu.chnu.awards.award.dto;

import java.util.List;

/**
 * The preview of a spreadsheet import; nothing is stored yet.
 *
 * @param checksum the checksum of the file and the mapping, sent back with the import
 * @param headers  the header cells, by column
 * @param mapping  the mapping the preview used: the one sent, or a guess from the headers
 * @param rows     the rows
 */
public record AwardImportPreview(String checksum, List<String> headers, ImportMapping mapping, List<ImportRow> rows) {

    /**
     * Copies the lists.
     *
     * @param checksum the checksum
     * @param headers  the headers
     * @param mapping  the mapping
     * @param rows     the rows
     */
    public AwardImportPreview {
        headers = List.copyOf(headers);
        rows = List.copyOf(rows);
    }
}
