package ua.edu.chnu.awards.document.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.service.AwardNotFoundException;
import ua.edu.chnu.awards.award.service.AwardOwnership;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.config.DocumentProperties;
import ua.edu.chnu.awards.document.dto.DocumentResponse;
import ua.edu.chnu.awards.document.entity.Document;
import ua.edu.chnu.awards.document.entity.DocumentFormat;
import ua.edu.chnu.awards.document.entity.DocumentType;
import ua.edu.chnu.awards.document.event.ObjectStored;
import ua.edu.chnu.awards.document.repository.DocumentRepository;

import lombok.RequiredArgsConstructor;

/**
 * Attaches files to the caller's drafts. The content is scanned for malware, checked and written to the storage
 * outside any database transaction, so a slow scanner or storage holds no connection or lock; the row is then
 * inserted in a short transaction under the award's row lock, which uploads, deletions and the submission of one
 * award share. The object is removed again when that transaction rolls back.
 */
@Service
@RequiredArgsConstructor
public class DocumentUpload {

    /** Prefix of every document object key. */
    public static final String KEY_PREFIX = "awards/";

    /** Longest description, in characters. */
    public static final int MAX_DESCRIPTION_LENGTH = 500;

    private final DocumentRepository documents;
    private final AwardOwnership ownership;
    private final DocumentContent content;
    private final ObjectStorage storage;
    private final DocumentProperties properties;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;
    private final MalwareScreening malware;
    private final UploadLimits limits;
    private final AccessScope access;

    /**
     * Attaches a file to the caller's draft.
     *
     * @param awardId     the draft
     * @param file        the uploaded file
     * @param type        what the document is
     * @param description optional note, at most {@value #MAX_DESCRIPTION_LENGTH} characters
     * @return the stored document
     * @throws AwardNotFoundException      when the award does not exist or is not the caller's
     * @throws ApiProblemException         429 {@code too-many-requests}, 409 {@code award-not-editable}, 400
     *                                     {@code empty-file}, 413 {@code file-too-large}, 409
     *                                     {@code storage-quota}, 422 {@code malware-detected}, 400
     *                                     {@code unsupported-type}, {@code content-mismatch},
     *                                     {@code invalid-parameter}, 409 {@code document-limit} or
     *                                     {@code duplicate-document}
     * @throws ScannerUnavailableException when the file cannot be scanned
     * @throws StorageUnavailableException when the content cannot be stored
     */
    public DocumentResponse upload(long awardId, MultipartFile file, DocumentType type, String description) {
        limits.checkRate(access.callerId());
        long ownerId = Objects.requireNonNull(transactions.execute(status -> {
            Award draft = ownership.lockedDraft(awardId);
            checkSize(file.getSize());
            limits.checkQuota(draft.getOwner().getId(), file.getSize());
            return draft.getOwner().getId();
        }));
        malware.check(awardId, ownerId, file);
        final Accepted accepted = Objects.requireNonNull(transactions.execute(status -> {
            ownership.lockedDraft(awardId);
            return accept(awardId, file, description);
        }));
        String key = KEY_PREFIX + awardId + "/" + UUID.randomUUID();
        storage.put(key, file, file.getSize(), accepted.format().mimeType());
        return transactions.execute(status -> {
            events.publishEvent(new ObjectStored(key));
            Award award = ownership.lockedDraft(awardId);
            checkRoom(awardId, accepted.checksum());
            limits.checkQuotaLocked(ownerId, file.getSize());
            return DocumentResponse.of(documents.saveAndFlush(Document.builder()
                .award(award)
                .fileName(accepted.fileName())
                .format(accepted.format())
                .mimeType(accepted.format().mimeType())
                .size(file.getSize())
                .storageBucket(storage.bucket())
                .storageKey(key)
                .checksum(accepted.checksum())
                .type(type)
                .description(accepted.description())
                .uploadedBy(award.getOwner())
                .build()));
        });
    }

    private Accepted accept(long awardId, MultipartFile file, String description) {
        DocumentContent.Facts facts = content.read(inputOf(file));
        DocumentFormat format = content.format(facts.head());
        String fileName = content.fileName(file.getOriginalFilename(), format);
        checkRoom(awardId, facts.checksum());
        return new Accepted(fileName, format, facts.checksum(), description(description));
    }

    private void checkSize(long size) {
        if (size == 0) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, "empty-file", "The file is empty");
        }
        if (size > properties.maxFileSize().toBytes()) {
            throw new ApiProblemException(HttpStatus.PAYLOAD_TOO_LARGE, "file-too-large",
                "The file is larger than the limit", Map.of("maxSize", properties.maxFileSize().toBytes()));
        }
    }

    private void checkRoom(long awardId, String checksum) {
        if (documents.countByAwardId(awardId) >= properties.maxFilesPerAward()) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "document-limit",
                "The award already has the most documents allowed",
                Map.of("limit", properties.maxFilesPerAward()));
        }
        documents.findFirstByAwardIdAndChecksum(awardId, checksum).ifPresent(existing -> {
            throw new ApiProblemException(HttpStatus.CONFLICT, "duplicate-document",
                "The file is already attached to the award", Map.of("documentId", existing.getId()));
        });
    }

    private static String description(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String note = description.strip();
        if (note.length() > MAX_DESCRIPTION_LENGTH) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-parameter",
                "The description is longer than " + MAX_DESCRIPTION_LENGTH + " characters",
                Map.of("parameter", "description"));
        }
        return note;
    }

    private static InputStream inputOf(MultipartFile file) {
        try {
            return file.getInputStream();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private record Accepted(String fileName, DocumentFormat format, String checksum, String description) {
    }
}
