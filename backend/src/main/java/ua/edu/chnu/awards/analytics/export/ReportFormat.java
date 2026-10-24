package ua.edu.chnu.awards.analytics.export;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;

import ua.edu.chnu.awards.common.web.ApiProblemException;

/**
 * The file formats of the analytics export.
 */
public enum ReportFormat {

    /** Comma-separated values, one long table. */
    CSV("csv", "text/csv"),
    /** An Excel workbook, one sheet per section. */
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    /** A printable A4 document. */
    PDF("pdf", "application/pdf");

    private final String extension;
    private final String mediaType;

    ReportFormat(String extension, String mediaType) {
        this.extension = extension;
        this.mediaType = mediaType;
    }

    /**
     * The file name extension, also the value of the {@code format} parameter.
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
    public static ReportFormat of(String value) {
        return Arrays.stream(values())
            .filter(format -> format.extension.equals(value == null ? null : value.toLowerCase(Locale.ROOT)))
            .findFirst()
            .orElseThrow(() -> new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-parameter",
                "The format must be csv, xlsx or pdf", Map.of("parameter", "format")));
    }
}
