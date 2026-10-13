package ua.edu.chnu.awards.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import ua.edu.chnu.awards.award.dto.SharedDocument;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.document.entity.Document;
import ua.edu.chnu.awards.document.entity.DocumentType;
import ua.edu.chnu.awards.document.repository.DocumentCount;
import ua.edu.chnu.awards.document.repository.DocumentRepository;

class DocumentsOfAwardsTest {

    private static final long AWARD_ID = 5L;

    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final DocumentService service = mock(DocumentService.class);
    private final DocumentSharing sharing = mock(DocumentSharing.class);
    private final DocumentsOfAwards awardDocuments = new DocumentsOfAwards(documents, service, sharing);

    @Test
    void ac2_5_countsComeFromTheStoredDocuments() {
        when(documents.countByAwardId(AWARD_ID)).thenReturn(2L);
        when(documents.countByAwardIds(Set.of(AWARD_ID, 6L))).thenReturn(List.of(new DocumentCount(AWARD_ID, 2L)));

        assertThat(awardDocuments.countOf(AWARD_ID)).isEqualTo(2L);
        assertThat(awardDocuments.countsOf(Set.of(AWARD_ID, 6L))).containsExactlyEntriesOf(Map.of(AWARD_ID, 2L));
    }

    @Test
    void ac2_5_aShownDocumentIsListedWithTheNameOfItsType() {
        Document shown = Document.builder().fileName("диплом.pdf").type(DocumentType.DIPLOMA)
            .mimeType("application/pdf").size(3L).description("оригінал").shownToColleagues(true).build();
        ReflectionTestUtils.setField(shown, "id", 7L);
        when(documents.findByAwardIdAndShownToColleaguesTrueOrderByUploadedAtAscIdAsc(AWARD_ID))
            .thenReturn(List.of(shown));

        assertThat(awardDocuments.shownToColleagues(AWARD_ID))
            .containsExactly(new SharedDocument(7L, "диплом.pdf", "DIPLOMA", "application/pdf", 3L, "оригінал"));
    }

    @Test
    void ac2_5_releaseAndHidingAreLeftToTheDocumentServices() {
        Award award = Award.builder().build();

        awardDocuments.releaseObjectsOf(AWARD_ID);
        awardDocuments.hideFromColleagues(award);

        verify(service).releaseObjectsOf(AWARD_ID);
        verify(sharing).clear(award);
    }
}
