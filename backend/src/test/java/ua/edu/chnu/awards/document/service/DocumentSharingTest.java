package ua.edu.chnu.awards.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.entity.AwardVisibility;
import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.award.service.AwardOwnership;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.document.dto.DocumentResponse;
import ua.edu.chnu.awards.document.dto.DocumentSharingUpdate;
import ua.edu.chnu.awards.document.entity.Document;
import ua.edu.chnu.awards.document.entity.DocumentFormat;
import ua.edu.chnu.awards.document.entity.DocumentType;
import ua.edu.chnu.awards.document.repository.DocumentRepository;
import ua.edu.chnu.awards.support.TestAwards;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.User;

class DocumentSharingTest {

    private static final long DOCUMENT_ID = 7L;
    private static final long AWARD_ID = TestAwards.AWARD_ID;

    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final AwardRepository awards = mock(AwardRepository.class);
    private final AwardOwnership ownership = mock(AwardOwnership.class);
    private final AuditService audit = mock(AuditService.class);
    private final DocumentSharing sharing = new DocumentSharing(documents, awards, ownership, audit);
    private final Organization department = TestUsers.organization(64L, OrganizationType.DEPARTMENT);
    private final User owner = TestUsers.person(21L, "owner@chnu.edu.ua", department);
    private Award award;
    private Document document;

    @BeforeEach
    void setUp() {
        award = TestAwards.award(owner, department).status(AwardStatus.APPROVED).build();
        award.setVisibility(AwardVisibility.UNIVERSITY);
        document = document(DOCUMENT_ID, false);
        when(documents.awardIdOf(DOCUMENT_ID)).thenReturn(Optional.of(AWARD_ID));
        when(documents.findById(DOCUMENT_ID)).thenReturn(Optional.of(document));
        when(awards.findForUpdate(AWARD_ID)).thenReturn(Optional.of(award));
        when(ownership.isOwn(award)).thenReturn(true);
    }

    @Test
    void ac1_ac4_theOwnerShowsADocumentOfASharedAwardAndTheChangeIsAudited() {
        DocumentResponse response = sharing.update(DOCUMENT_ID, new DocumentSharingUpdate(true));

        assertThat(response.shownToColleagues()).isTrue();
        assertThat(document.isShownToColleagues()).isTrue();
        verify(audit).record(AuditAction.DOCUMENT_SHARING_CHANGED, AuditEntityConstants.DOCUMENTS, owner.getId(),
            DOCUMENT_ID, Map.of("awardId", AWARD_ID, "from", false, "to", true));
    }

    @Test
    void ac4_theSameValueAgainWritesNoAuditRow() {
        DocumentResponse response = sharing.update(DOCUMENT_ID, new DocumentSharingUpdate(false));

        assertThat(response.shownToColleagues()).isFalse();
        verify(audit, never()).record(any(), any(), anyLong(), anyLong(), anyMap());
    }

    @Test
    void ac1_aDocumentOfSomeoneElseOrAnUnknownOneIsNotFound() {
        when(ownership.isOwn(award)).thenReturn(false);
        assertThatThrownBy(() -> sharing.update(DOCUMENT_ID, new DocumentSharingUpdate(true)))
            .isInstanceOf(DocumentNotFoundException.class);

        when(documents.awardIdOf(8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> sharing.update(8L, new DocumentSharingUpdate(true)))
            .isInstanceOf(DocumentNotFoundException.class);
        assertThat(document.isShownToColleagues()).isFalse();
        verifyNoInteractions(audit);
    }

    @Test
    void ac1_aMissingChoiceIsRejected() {
        assertThatThrownBy(() -> sharing.update(DOCUMENT_ID, null))
            .isInstanceOfSatisfying(ApiProblemException.class,
                problem -> assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        assertThatThrownBy(() -> sharing.update(DOCUMENT_ID, new DocumentSharingUpdate(null)))
            .isInstanceOf(ApiProblemException.class);
        verifyNoInteractions(audit);
    }

    @Test
    void ac1_aPrivateAwardKeepsItsDocumentsHidden() {
        award.setVisibility(AwardVisibility.PRIVATE);

        assertSharingFixed();
    }

    @ParameterizedTest
    @EnumSource(value = AwardStatus.class, names = "APPROVED", mode = EnumSource.Mode.EXCLUDE)
    void ac1_anAwardThatIsNotApprovedKeepsItsDocumentsHidden(AwardStatus status) {
        award.setStatus(status);

        assertSharingFixed();
    }

    @Test
    void ac1_aUnitAwardHasNoDocumentChoice() {
        award.setVisibility(AwardVisibility.PRIVATE);
        award.setRecipientOrganizationId(department.getId());

        assertSharingFixed();
    }

    @Test
    void ac3_clearingHidesEveryShownDocumentWithAnAuditRowEach() {
        Document other = document(9L, true);
        document = document(DOCUMENT_ID, true);
        when(documents.findByAwardIdAndShownToColleaguesTrueOrderByUploadedAtAscIdAsc(AWARD_ID))
            .thenReturn(List.of(document, other));

        sharing.clear(award);

        assertThat(document.isShownToColleagues()).isFalse();
        assertThat(other.isShownToColleagues()).isFalse();
        verify(audit).record(AuditAction.DOCUMENT_SHARING_CHANGED, AuditEntityConstants.DOCUMENTS, owner.getId(),
            DOCUMENT_ID, Map.of("awardId", AWARD_ID, "from", true, "to", false));
        verify(audit).record(AuditAction.DOCUMENT_SHARING_CHANGED, AuditEntityConstants.DOCUMENTS, owner.getId(),
            9L, Map.of("awardId", AWARD_ID, "from", true, "to", false));
    }

    private void assertSharingFixed() {
        assertThatThrownBy(() -> sharing.update(DOCUMENT_ID, new DocumentSharingUpdate(true)))
            .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(problem.getType()).isEqualTo("sharing-fixed");
            });
        assertThat(document.isShownToColleagues()).isFalse();
        verifyNoInteractions(audit);
    }

    private Document document(long id, boolean shown) {
        Document created = Document.builder().award(award).fileName("диплом.pdf").format(DocumentFormat.PDF)
            .mimeType("application/pdf").size(3L).storageBucket("award-documents").storageKey("awards/5/" + id)
            .type(DocumentType.CERTIFICATE).uploadedBy(owner).shownToColleagues(shown).build();
        ReflectionTestUtils.setField(created, "id", id);
        return created;
    }
}
