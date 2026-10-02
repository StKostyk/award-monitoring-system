package ua.edu.chnu.awards.document.entity;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * File formats a document may have, as found in its content; stored in {@code documents.file_type}.
 */
public enum DocumentFormat {
    PDF("application/pdf", "pdf"),
    JPEG("image/jpeg", "jpg", "jpeg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private final String mimeType;
    private final String extension;
    private final Set<String> extensions;

    DocumentFormat(String mimeType, String extension, String... aliases) {
        this.mimeType = mimeType;
        this.extension = extension;
        this.extensions = Stream.concat(Stream.of(extension), Arrays.stream(aliases))
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Whether an extension belongs to any supported format.
     *
     * @param extension the extension without its dot, any case
     * @return true for pdf, jpg, jpeg, png and webp
     */
    public static boolean isKnownExtension(String extension) {
        return Arrays.stream(values()).anyMatch(format -> format.accepts(extension));
    }

    /**
     * The media type the content is served with.
     *
     * @return the media type
     */
    public String mimeType() {
        return mimeType;
    }

    /**
     * The extension given to a file name that has none of this format.
     *
     * @return the extension without its dot
     */
    public String extension() {
        return extension;
    }

    /**
     * Whether a file name extension names this format.
     *
     * @param candidate the extension without its dot, any case
     * @return true when it matches
     */
    public boolean accepts(String candidate) {
        return extensions.contains(candidate.toLowerCase(Locale.ROOT));
    }
}
