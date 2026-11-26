package ua.edu.chnu.awards.document.service;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.AwardVisibility;
import ua.edu.chnu.awards.award.service.AwardLookup;
import ua.edu.chnu.awards.award.service.AwardOwnership;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.common.web.FieldViolation;
import ua.edu.chnu.awards.document.dto.DocumentResponse;
import ua.edu.chnu.awards.document.dto.DocumentSharingUpdate;
import ua.edu.chnu.awards.document.entity.Document;
import ua.edu.chnu.awards.document.repository.DocumentRepository;

import lombok.RequiredArgsConstructor;

/**
 * The owner's choice of which documents of a shared personal award colleagues may open. The choice is not award
 * content; the audit row of each effective change is the record of consent.
 */
@Service
@RequiredArgsConstructor
public class DocumentSharing {

    private final DocumentRepository documents;
    private final AwardLookup awards;
    private final AwardOwnership ownership;
    private final AuditService audit;

    /**
     * Shows or hides a document of the caller's shared award.
     *
     * @param id   the document
     * @param body the choice
     * @return the document with its choice
     * @throws DocumentNotFoundException when it does not exist or its award is not the caller's
     * @throws ApiProblemException       422 {@code validation-failed} without a choice, 409 {@code sharing-fixed}
     *                                   when the award is not an approved personal award shared with colleagues
     */
    @Transactional
    public DocumentResponse update(long id, DocumentSharingUpdate body) {
        Boolean shown = body == null ? null : body.shownToColleagues();
        if (shown == null) {
            throw ApiProblemException.validationFailed("The choice is missing", List.of(
                new FieldViolation("shownToColleagues", "required", "Choose whether colleagues see the document")));
        }
        Award award = documents.awardIdOf(id).flatMap(awards::findForUpdate).filter(ownership::isOwn)
            .orElseThrow(() -> new DocumentNotFoundException(id));
        requireShared(award);
        Document document = documents.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
        if (document.isShownToColleagues() != shown) {
            document.showToColleagues(shown);
            recordChange(award, document);
        }
        return DocumentResponse.of(document);
    }

    /**
     * Hides every shown document of an award that is no longer shared, with an audit row each.
     *
     * @param award the award, locked by the caller
     */
    @Transactional
    public void clear(Award award) {
        for (Document document : documents.findByAwardIdAndShownToColleaguesTrueOrderByUploadedAtAscIdAsc(
            award.getId())) {
            document.showToColleagues(false);
            recordChange(award, document);
        }
    }

    private void recordChange(Award award, Document document) {
        boolean shown = document.isShownToColleagues();
        audit.record(AuditAction.DOCUMENT_SHARING_CHANGED, AuditEntityConstants.DOCUMENTS, award.getOwner().getId(),
            document.getId(), Map.of("awardId", award.getId(), "from", !shown, "to", shown));
    }

    private static void requireShared(Award award) {
        if (award.getStatus() != AwardStatus.APPROVED || award.isUnitAward()
            || award.getVisibility() == AwardVisibility.PRIVATE) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "sharing-fixed",
                "Documents are shown only on an approved personal award shared with colleagues",
                Map.of("awardStatus", award.getStatus().name(), "visibility", award.getVisibility().name()));
        }
    }
}
