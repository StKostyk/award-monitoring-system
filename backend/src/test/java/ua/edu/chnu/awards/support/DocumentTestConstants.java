package ua.edu.chnu.awards.support;

import java.nio.charset.StandardCharsets;

import org.springframework.mock.web.MockMultipartFile;

/**
 * File contents and multipart files shared by the document tests.
 */
@SuppressWarnings("PMD.DataClass")
public final class DocumentTestConstants {

    /** Content recognised as a PDF. */
    public static final byte[] PDF = "%PDF-1.7 certificate".getBytes(StandardCharsets.US_ASCII);
    /** Content recognised as a PNG: the signature and the start of the header chunk. */
    public static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0x0D};

    private DocumentTestConstants() {
    }

    /**
     * A multipart part named {@code file} as a browser sends it without knowing the type.
     *
     * @param name    the file name
     * @param content the content
     * @return the part
     */
    public static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }
}
