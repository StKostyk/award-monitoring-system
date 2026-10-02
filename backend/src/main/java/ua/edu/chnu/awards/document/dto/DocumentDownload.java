package ua.edu.chnu.awards.document.dto;

import java.io.InputStream;

/**
 * The content of a document opened for download; the caller closes the stream.
 *
 * @param fileName name to save the file under
 * @param mimeType media type of the content
 * @param size     size in bytes
 * @param content  the content, streamed from the storage
 */
public record DocumentDownload(String fileName, String mimeType, long size, InputStream content) {
}
