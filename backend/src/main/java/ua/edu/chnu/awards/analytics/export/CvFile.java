package ua.edu.chnu.awards.analytics.export;

/**
 * A produced CV file.
 *
 * @param name      the file name
 * @param mediaType the media type
 * @param content   the bytes
 */
public record CvFile(String name, String mediaType, byte[] content) {

    /**
     * Keeps a copy of the content.
     *
     * @param name      the file name
     * @param mediaType the media type
     * @param content   the bytes
     */
    public CvFile {
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
