package ua.edu.chnu.awards.award.dto;

/**
 * A document its owner shows to colleagues, as listed on the shared award.
 *
 * @param id          document identifier, the download path is {@code /api/v1/documents/{id}}
 * @param fileName    the uploaded name, cleaned
 * @param type        what the document is, the name of its document type
 * @param mimeType    media type found in the content
 * @param size        size in bytes
 * @param description the uploader's note, null when none
 */
public record SharedDocument(long id, String fileName, String type, String mimeType, long size,
                             String description) {
}
