package ua.edu.chnu.awards.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import ua.edu.chnu.awards.audit.entity.AuditAction;
import ua.edu.chnu.awards.audit.entity.AuditEntityConstants;
import ua.edu.chnu.awards.audit.service.AuditService;
import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.award.service.AwardNotFoundException;
import ua.edu.chnu.awards.award.service.AwardOwnership;
import ua.edu.chnu.awards.common.web.ApiProblemException;
import ua.edu.chnu.awards.document.dto.DocumentDownload;
import ua.edu.chnu.awards.document.entity.Document;
import ua.edu.chnu.awards.document.entity.DocumentFormat;
import ua.edu.chnu.awards.document.entity.DocumentType;
import ua.edu.chnu.awards.document.event.ObjectsReleased;
import ua.edu.chnu.awards.document.repository.DocumentRepository;
import ua.edu.chnu.awards.support.TestAwards;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

class DocumentServiceTest {

    private static final long OWNER_ID = 21L;
    private static final long READER_ID = 30L;
    private static final long AWARD_ID = TestAwards.AWARD_ID;
    private static final long DOCUMENT_ID = 7L;
    private static final String KEY = "awards/5/0b7c";

    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final AwardRepository awards = mock(AwardRepository.class);
    private final AccessScope access = mock(AccessScope.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final AuditService audit = mock(AuditService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final DocumentService service = new DocumentService(documents,
        new AwardOwnership(awards, mock(UserRepository.class), access), storage, audit, access, events);
    private final Organization department = TestUsers.organization(64L, OrganizationType.DEPARTMENT);
    private final User owner = TestUsers.person(OWNER_ID, "owner@chnu.edu.ua", department);

    @BeforeEach
    void setUp() {
        when(access.callerId()).thenReturn(OWNER_ID);
    }

    @Test
    void ac1_7_theOwnerListsTheDocumentsOfTheirDraft() {
        Award draft = award(AwardStatus.DRAFT);
        when(awards.findById(AWARD_ID)).thenReturn(Optional.of(draft));
        when(documents.findByAwardIdOrderByUploadedAtAscIdAsc(AWARD_ID)).thenReturn(List.of(document(draft)));

        assertThat(service.list(AWARD_ID)).singleElement()
            .satisfies(response -> assertThat(response.fileName()).isEqualTo("диплом.pdf"));
    }

    @Test
    void ac1_7_aDraftOfSomeoneElseIsMissingEvenForAReaderOfItsOrganisation() {
        when(access.callerId()).thenReturn(READER_ID);
        when(access.canReadAwards(department.getId())).thenReturn(true);
        when(awards.findById(AWARD_ID)).thenReturn(Optional.of(award(AwardStatus.DRAFT)));

        assertThatThrownBy(() -> service.list(AWARD_ID)).isInstanceOf(AwardNotFoundException.class);
        verify(documents, never()).findByAwardIdOrderByUploadedAtAscIdAsc(AWARD_ID);
    }

    @Test
    void ac1_8_aReaderOfASubmittedAwardOpensTheContentAndTheDownloadIsRecorded() {
        when(access.callerId()).thenReturn(READER_ID);
        when(access.canReadAwards(department.getId())).thenReturn(true);
        Document document = document(award(AwardStatus.PENDING));
        when(documents.findWithAwardById(DOCUMENT_ID)).thenReturn(Optional.of(document));
        InputStream content = new ByteArrayInputStream(new byte[] {1});
        when(storage.get(KEY)).thenReturn(Optional.of(content));

        DocumentDownload download = service.open(DOCUMENT_ID);

        assertThat(download.content()).isSameAs(content);
        assertThat(download.fileName()).isEqualTo("диплом.pdf");
        assertThat(download.mimeType()).isEqualTo("application/pdf");
        verify(audit).record(AuditAction.DOCUMENT_DOWNLOAD, AuditEntityConstants.DOCUMENTS, READER_ID, DOCUMENT_ID,
            Map.of("awardId", AWARD_ID));
    }

    @Test
    void ac1_8_aDocumentOfAnUnreadableAwardIsMissingAndNothingIsRecorded() {
        when(access.callerId()).thenReturn(READER_ID);
        when(documents.findWithAwardById(DOCUMENT_ID)).thenReturn(Optional.of(document(award(AwardStatus.PENDING))));

        assertThatThrownBy(() -> service.open(DOCUMENT_ID)).isInstanceOf(DocumentNotFoundException.class);
        assertThatThrownBy(() -> service.open(8L)).isInstanceOf(DocumentNotFoundException.class);
        verifyNoInteractions(audit, storage);
    }

    @Test
    void aMissingObjectIsReportedAsMissingContent() {
        when(documents.findWithAwardById(DOCUMENT_ID)).thenReturn(Optional.of(document(award(AwardStatus.DRAFT))));
        when(storage.get(KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.open(DOCUMENT_ID)).isInstanceOf(ApiProblemException.class)
            .extracting("type").isEqualTo("document-content-missing");
    }

    @Test
    void ac1_9_theOwnerDeletesADocumentOfTheirDraftAndItsObjectIsReleased() {
        Award draft = award(AwardStatus.DRAFT);
        Document document = document(draft);
        when(documents.findById(DOCUMENT_ID)).thenReturn(Optional.of(document));
        when(awards.findForUpdate(AWARD_ID)).thenReturn(Optional.of(draft));

        service.delete(DOCUMENT_ID);

        verify(documents).delete(document);
        verify(events).publishEvent(new ObjectsReleased(List.of(KEY)));
    }

    @Test
    void ac1_9_aSubmittedAwardKeepsItsDocumentsAndSomeoneElseFindsNone() {
        Award submitted = award(AwardStatus.PENDING);
        when(documents.findById(DOCUMENT_ID)).thenReturn(Optional.of(document(submitted)));
        when(awards.findForUpdate(AWARD_ID)).thenReturn(Optional.of(submitted));
        assertThatThrownBy(() -> service.delete(DOCUMENT_ID)).isInstanceOf(ApiProblemException.class)
            .extracting("type").isEqualTo("award-not-editable");

        when(access.callerId()).thenReturn(READER_ID);
        assertThatThrownBy(() -> service.delete(DOCUMENT_ID)).isInstanceOf(DocumentNotFoundException.class);
        assertThatThrownBy(() -> service.delete(8L)).isInstanceOf(DocumentNotFoundException.class);
        verify(documents, never()).delete(any());
        verifyNoInteractions(events);
    }

    @Test
    void ac1_10_theObjectsOfADeletedDraftAreReleasedWhenItHasAny() {
        when(documents.storageKeysOfAward(AWARD_ID)).thenReturn(List.of(KEY, "awards/5/1c"));
        service.releaseObjectsOf(AWARD_ID);
        verify(events).publishEvent(new ObjectsReleased(List.of(KEY, "awards/5/1c")));

        when(documents.storageKeysOfAward(6L)).thenReturn(List.of());
        service.releaseObjectsOf(6L);
        verify(events, never()).publishEvent(new ObjectsReleased(List.of()));
    }

    private Award award(AwardStatus status) {
        return TestAwards.award(owner, department).status(status).build();
    }

    private Document document(Award award) {
        Document document = Document.builder().award(award).fileName("диплом.pdf").format(DocumentFormat.PDF)
            .mimeType("application/pdf").size(3L).storageBucket("award-documents").storageKey(KEY)
            .type(DocumentType.CERTIFICATE).uploadedBy(owner).build();
        ReflectionTestUtils.setField(document, "id", DOCUMENT_ID);
        return document;
    }
}
