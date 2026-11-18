package ua.edu.chnu.awards.award;

/**
 * The malware scan of an imported spreadsheet; implemented by the module that scans uploads.
 */
public interface ImportScreening {

    /**
     * Scans a spreadsheet of an award import, which is read but never stored.
     *
     * @param userId  the uploader
     * @param content the file content
     */
    void checkImport(long userId, byte[] content);
}
