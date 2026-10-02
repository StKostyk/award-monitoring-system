package ua.edu.chnu.awards.document.service;

import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.service.AwardNotFoundException;
import ua.edu.chnu.awards.award.service.AwardOwnership;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.document.dto.DocumentDownload;
import ua.edu.chnu.awards.document.dto.DocumentResponse;
import ua.edu.chnu.awards.document.entity.Document;
import ua.edu.chnu.awards.document.event.ObjectsReleased;
import ua.edu.chnu.awards.document.repository.DocumentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Documents of awards after their upload: listing and download for everyone who may read the award, deletion
 * on the owner's draft.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documents;
    private final AwardOwnership ownership;
    private final ObjectStorage storage;
    private final AuditService audit;
    private final AccessScope access;
    private final ApplicationEventPublisher events;

    /**
     * The documents of an award the caller may read, oldest first.
     *
     * @param awardId the award
     * @return the documents
     * @throws AwardNotFoundException when the award does not exist or is not readable by the caller
     */
    @Transactional(readOnly = true)
    public List<DocumentResponse> list(long awardId) {
        ownership.readable(awardId);
        return documents.findByAwardIdOrderByUploadedAtAscIdAsc(awardId).stream().map(DocumentResponse::of)
            .toList();
    }

    /**
     * Opens a document of an award the caller may read and records the download.
     *
     * @param id the document
     * @return the name, type, size and content stream
     * @throws DocumentNotFoundException   when it does not exist or its award is not readable by the caller
     * @throws ApiProblemException         404 {@code document-content-missing} when its object is gone
     * @throws StorageUnavailableException when the storage cannot be reached
     */
    @Transactional
    public DocumentDownload open(long id) {
        Document document = documents.findWithAwardById(id).filter(found -> ownership.isReadable(found.getAward()))
            .orElseThrow(() -> new DocumentNotFoundException(id));
        audit.record(AuditAction.DOCUMENT_DOWNLOAD, AuditEntityConstants.DOCUMENTS, access.callerId(), id,
            Map.of("awardId", document.getAward().getId()));
        return storage.get(document.getStorageKey())
            .map(stream -> new DocumentDownload(document.getFileName(), document.getMimeType(), document.getSize(),
                stream))
            .orElseThrow(() -> {
                log.error("Object {} of document {} is missing from the storage", document.getStorageKey(), id);
                return new ApiProblemException(HttpStatus.NOT_FOUND, "document-content-missing",
                    "The content of the document is no longer available");
            });
    }

    /**
     * Deletes a document of the caller's draft; its object is removed after the commit.
     *
     * @param id the document
     * @throws DocumentNotFoundException when it does not exist or its award is not the caller's
     * @throws ApiProblemException       409 {@code award-not-editable} when the award was submitted
     */
    @Transactional
    public void delete(long id) {
        Document document = documents.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
        try {
            ownership.lockedDraft(document.getAward().getId());
        } catch (AwardNotFoundException notOwn) {
            throw new DocumentNotFoundException(id, notOwn);
        }
        documents.delete(document);
        events.publishEvent(new ObjectsReleased(List.of(document.getStorageKey())));
    }

    /**
     * Schedules the removal of the objects of an award that is being deleted, for after the commit.
     *
     * @param awardId the award
     */
    @Transactional
    public void releaseObjectsOf(long awardId) {
        List<String> keys = documents.storageKeysOfAward(awardId);
        if (!keys.isEmpty()) {
            events.publishEvent(new ObjectsReleased(keys));
        }
    }
}
