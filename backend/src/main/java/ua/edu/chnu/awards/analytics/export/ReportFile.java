package ua.edu.chnu.awards.analytics.export;

/**
 * A produced report file.
 *
 * @param name    the file name
 * @param format  the format
 * @param content the file content
 */
public record ReportFile(String name, ReportFormat format, byte[] content) {

    /**
     * Keeps a copy of the content.
     */
    public ReportFile {
        content = content.clone();
    }

    /**
     * The file content.
     *
     * @return a copy of the content
     */
    @Override
    public byte[] content() {
        return content.clone();
    }
}
