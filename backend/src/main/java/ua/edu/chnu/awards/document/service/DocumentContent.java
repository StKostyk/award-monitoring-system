package ua.edu.chnu.awards.document.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.document.entity.DocumentFormat;

/**
 * What an uploaded file is: its format from the leading bytes, its SHA-256, and the name it is kept under.
 */
@Component
public class DocumentContent {

    /** Longest stored file name, in characters. */
    public static final int MAX_NAME_LENGTH = 255;

    private static final int HEAD_LENGTH = 12;
    private static final int BUFFER_SIZE = 8192;
    private static final int WEBP_TAG_OFFSET = 8;
    private static final byte[] PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP = "WEBP".getBytes(StandardCharsets.US_ASCII);
    private static final Pattern INVISIBLE = Pattern.compile("[\\p{Cc}\\p{Cf}]");
    private static final String FALLBACK_NAME = "document";

    /**
     * Reads the content once: its first bytes and its SHA-256.
     *
     * @param content the content; closed afterwards
     * @return the facts
     */
    public Facts read(InputStream content) {
        MessageDigest digest = sha256();
        byte[] head = new byte[HEAD_LENGTH];
        int headLength = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream in = content) {
            for (int read = in.read(buffer); read != -1; read = in.read(buffer)) {
                int copied = Math.min(read, HEAD_LENGTH - headLength);
                System.arraycopy(buffer, 0, head, headLength, copied);
                headLength += copied;
                digest.update(buffer, 0, read);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return new Facts(Arrays.copyOf(head, headLength), HexFormat.of().formatHex(digest.digest()));
    }

    /**
     * The format of the content by its leading bytes.
     *
     * @param head the first bytes of the content
     * @return the format
     * @throws ApiProblemException 400 {@code unsupported-type} when it is not PDF, JPEG, PNG or WEBP
     */
    public DocumentFormat format(byte[] head) {
        if (startsWith(head, PDF, 0)) {
            return DocumentFormat.PDF;
        }
        if (startsWith(head, JPEG, 0)) {
            return DocumentFormat.JPEG;
        }
        if (startsWith(head, PNG, 0)) {
            return DocumentFormat.PNG;
        }
        if (startsWith(head, RIFF, 0) && startsWith(head, WEBP, WEBP_TAG_OFFSET)) {
            return DocumentFormat.WEBP;
        }
        throw new ApiProblemException(HttpStatus.BAD_REQUEST, "unsupported-type",
            "Only PDF, JPEG, PNG and WEBP files are accepted");
    }

    /**
     * The name a file is kept under: the last path segment without control or format characters, with the
     * extension of its content, at most {@value #MAX_NAME_LENGTH} characters with the extension kept.
     *
     * @param original the name sent by the client, may be null
     * @param format   the format of the content
     * @return the name
     * @throws ApiProblemException 400 {@code content-mismatch} when the extension names another supported format
     */
    public String fileName(String original, DocumentFormat format) {
        String name = lastSegment(original == null ? "" : original);
        name = stripLeadingDots(INVISIBLE.matcher(name).replaceAll("").strip());
        if (name.isEmpty()) {
            return FALLBACK_NAME + "." + format.extension();
        }
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1);
        if (!format.accepts(extension)) {
            if (DocumentFormat.isKnownExtension(extension)) {
                throw new ApiProblemException(HttpStatus.BAD_REQUEST, "content-mismatch",
                    "The file extension does not match its content",
                    Map.of("detectedType", format.name()));
            }
            extension = format.extension();
            name = name + "." + extension;
        }
        return shorten(name, extension);
    }

    private static String lastSegment(String name) {
        return name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
    }

    private static String stripLeadingDots(String name) {
        int start = 0;
        while (start < name.length() && (name.charAt(start) == '.' || Character.isWhitespace(name.charAt(start)))) {
            start++;
        }
        return name.substring(start);
    }

    private static String shorten(String name, String extension) {
        if (name.length() <= MAX_NAME_LENGTH) {
            return name;
        }
        int end = MAX_NAME_LENGTH - extension.length() - 1;
        if (Character.isLowSurrogate(name.charAt(end))) {
            end--;
        }
        return name.substring(0, end) + "." + extension;
    }

    private static boolean startsWith(byte[] head, byte[] signature, int offset) {
        return head.length >= offset + signature.length
            && Arrays.equals(head, offset, offset + signature.length, signature, 0, signature.length);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    /**
     * What reading the content found.
     *
     * @param head     its first bytes (fewer for a short file)
     * @param checksum its SHA-256, hex
     */
    public record Facts(byte[] head, String checksum) {

        /**
         * Keeps a copy of the leading bytes.
         */
        public Facts {
            head = head.clone();
        }

        /**
         * The first bytes of the content.
         *
         * @return a copy of them
         */
        @Override
        public byte[] head() {
            return head.clone();
        }
    }
}
