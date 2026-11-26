package ua.edu.chnu.awards.document.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ua.edu.chnu.awards.award.AwardDocuments;
import ua.edu.chnu.awards.award.dto.SharedDocument;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.document.entity.Document;
import ua.edu.chnu.awards.document.repository.DocumentCount;
import ua.edu.chnu.awards.document.repository.DocumentRepository;

import lombok.RequiredArgsConstructor;

/**
 * Answers the award workflow's questions about attached documents.
 */
@Component
@RequiredArgsConstructor
public class DocumentsOfAwards implements AwardDocuments {

    private final DocumentRepository documents;
    private final DocumentService service;
    private final DocumentSharing sharing;

    @Override
    @Transactional(readOnly = true)
    public long countOf(long awardId) {
        return documents.countByAwardId(awardId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> countsOf(Collection<Long> awardIds) {
        return documents.countByAwardIds(awardIds).stream()
            .collect(Collectors.toMap(DocumentCount::awardId, DocumentCount::count));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SharedDocument> shownToColleagues(long awardId) {
        return documents.findByAwardIdAndShownToColleaguesTrueOrderByUploadedAtAscIdAsc(awardId).stream()
            .map(DocumentsOfAwards::listing).toList();
    }

    @Override
    public void releaseObjectsOf(long awardId) {
        service.releaseObjectsOf(awardId);
    }

    @Override
    public void hideFromColleagues(Award award) {
        sharing.clear(award);
    }

    private static SharedDocument listing(Document document) {
        return new SharedDocument(document.getId(), document.getFileName(), document.getType().name(),
            document.getMimeType(), document.getSize(), document.getDescription());
    }
}
