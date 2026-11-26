package ua.edu.chnu.awards.support;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import org.apache.http.entity.mime.HttpMultipartMode;

import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;

/**
 * Document requests for functional tests: uploads as a browser sends them and file contents that the server
 * recognises by their leading bytes. The rest of every file is random, so two files never share a checksum.
 */
public final class DocumentApi {

    public static final String DOCUMENTS = "/api/v1/documents/";
    /** Size of the generated files unless a test needs another one. */
    public static final int SMALL = 64;

    private static final byte[] PDF_HEAD = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] JPEG_HEAD = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final byte[] PNG_HEAD = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] WEBP_HEAD = {'R', 'I', 'F', 'F', '$', 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};
    private static final RestAssuredConfig BROWSER_MULTIPART = RestAssuredConfig.config()
        .httpClient(HttpClientConfig.httpClientConfig().httpMultipartMode(HttpMultipartMode.BROWSER_COMPATIBLE));
    private static final Random RANDOM = new Random();

    private DocumentApi() {
    }

    /**
     * Uploads a file to an award as a browser does: UTF-8 file name, no part content type.
     *
     * @param token   the uploader's access token
     * @param award   the award id
     * @param name    the file name
     * @param content the file content
     * @param type    the document type
     * @return the response
     */
    public static Response upload(String token, long award, String name, byte[] content, String type) {
        return AbstractFunctionalTest.as(token).config(BROWSER_MULTIPART)
            .contentType("multipart/form-data; charset=UTF-8")
            .multiPart("file", name, content, "application/octet-stream")
            .multiPart("type", type)
            .post(AwardApi.AWARDS + "/" + award + "/documents");
    }

    /**
     * A PDF of the given size.
     *
     * @param size the length in bytes
     * @return the content
     */
    public static byte[] pdf(int size) {
        return withHead(PDF_HEAD, size);
    }

    /**
     * A small JPEG.
     *
     * @return the content
     */
    public static byte[] jpeg() {
        return withHead(JPEG_HEAD, SMALL);
    }

    /**
     * A small PNG.
     *
     * @return the content
     */
    public static byte[] png() {
        return withHead(PNG_HEAD, SMALL);
    }

    /**
     * A small WEBP image.
     *
     * @return the content
     */
    public static byte[] webp() {
        return withHead(WEBP_HEAD, SMALL);
    }

    private static byte[] withHead(byte[] head, int size) {
        byte[] content = new byte[size];
        RANDOM.nextBytes(content);
        System.arraycopy(head, 0, content, 0, head.length);
        return content;
    }
}
