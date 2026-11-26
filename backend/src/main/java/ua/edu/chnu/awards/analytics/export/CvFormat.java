package ua.edu.chnu.awards.analytics.export;

import java.util.Arrays;
import java.util.Map;

import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.common.web.ApiProblemException;

/**
 * The file formats of the CV export.
 */
public enum CvFormat {

    /** A printable A4 document. */
    PDF("pdf", "pdf", "application/pdf"),
    /** Plain text for pasting into a CV. */
    TEXT("text", "txt", "text/plain;charset=UTF-8");

    private final String code;
    private final String extension;
    private final String mediaType;

    CvFormat(String code, String extension, String mediaType) {
        this.code = code;
        this.extension = extension;
        this.mediaType = mediaType;
    }

    /**
     * The value of the {@code format} parameter.
     *
     * @return the code
     */
    public String code() {
        return code;
    }

    /**
     * The file name extension.
     *
     * @return the extension without the dot
     */
    public String extension() {
        return extension;
    }

    /**
     * The media type of the file.
     *
     * @return the media type
     */
    public String mediaType() {
        return mediaType;
    }

    /**
     * Reads the {@code format} parameter.
     *
     * @param value the parameter, or null
     * @return the format
     * @throws ApiProblemException 400 {@code invalid-parameter} naming {@code format}
     */
    public static CvFormat of(String value) {
        return Arrays.stream(values())
            .filter(format -> format.code.equals(value))
            .findFirst()
            .orElseThrow(() -> new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-parameter",
                "The format must be pdf or text", Map.of("parameter", "format")));
    }
}
