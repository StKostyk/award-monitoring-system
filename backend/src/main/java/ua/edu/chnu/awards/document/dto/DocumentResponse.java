package ua.edu.chnu.awards.document.dto;

import java.time.Instant;

import ua.edu.chnu.awards.award.dto.UserRef;
import ua.edu.chnu.awards.document.entity.Document;
import ua.edu.chnu.awards.document.entity.DocumentType;

/**
 * A document of an award, without its content.
 *
 * @param id          document identifier
 * @param awardId     the award it belongs to
 * @param fileName    the uploaded name, cleaned
 * @param type        what the document is
 * @param mimeType    media type found in the content
 * @param size        size in bytes
 * @param description the uploader's note, null when none
 * @param uploadedAt  when it was uploaded
 * @param uploadedBy  who uploaded it
 */
public record DocumentResponse(long id, long awardId, String fileName, DocumentType type, String mimeType,
                               long size, String description, Instant uploadedAt, UserRef uploadedBy) {

    /**
     * The response of a stored document.
     *
     * @param document the document with its uploader
     * @return the response
     */
    public static DocumentResponse of(Document document) {
        return new DocumentResponse(document.getId(), document.getAward().getId(), document.getFileName(),
            document.getType(), document.getMimeType(), document.getSize(), document.getDescription(),
            document.getUploadedAt(), UserRef.of(document.getUploadedBy()));
    }
}
