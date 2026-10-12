package ua.edu.chnu.awards.document.dto;

import ua.edu.chnu.awards.document.entity.Document;
import ua.edu.chnu.awards.document.entity.DocumentType;

/**
 * A document its owner shows to colleagues, as listed on the shared award.
 *
 * @param id          document identifier, the download path is {@code /api/v1/documents/{id}}
 * @param fileName    the uploaded name, cleaned
 * @param type        what the document is
 * @param mimeType    media type found in the content
 * @param size        size in bytes
 * @param description the uploader's note, null when none
 */
public record SharedDocument(long id, String fileName, DocumentType type, String mimeType, long size,
                             String description) {

    /**
     * The listing of a shown document.
     *
     * @param document the document
     * @return the listing
     */
    public static SharedDocument of(Document document) {
        return new SharedDocument(document.getId(), document.getFileName(), document.getType(),
            document.getMimeType(), document.getSize(), document.getDescription());
    }
}
