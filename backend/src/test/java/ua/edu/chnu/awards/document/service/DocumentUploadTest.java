package ua.edu.chnu.awards.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.unit.DataSize;

import ua.edu.chnu.awards.authz.AccessScope;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.award.service.AwardNotFoundException;
import ua.edu.chnu.awards.award.service.AwardOwnership;
import ua.edu.chnu.awards.config.DocumentProperties;
import ua.edu.chnu.awards.document.dto.DocumentResponse;
import ua.edu.chnu.awards.document.entity.Document;
import ua.edu.chnu.awards.document.entity.DocumentFormat;
import ua.edu.chnu.awards.document.entity.DocumentType;
import ua.edu.chnu.awards.document.event.ObjectStored;
import ua.edu.chnu.awards.document.repository.DocumentRepository;
import ua.edu.chnu.awards.support.TestAwards;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.OrganizationType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

class DocumentUploadTest {

    private static final long OWNER_ID = 21L;
    private static final long AWARD_ID = TestAwards.AWARD_ID;
    private static final long DOCUMENT_ID = 7L;
    private static final int LIMIT = 10;
    private static final byte[] PDF = "%PDF-1.7 certificate".getBytes(StandardCharsets.US_ASCII);
    private static final String TYPE = "type";

    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final AwardRepository awards = mock(AwardRepository.class);
    private final AccessScope access = mock(AccessScope.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final MalwareScreening malware = mock(MalwareScreening.class);
    private final UploadLimits limits = mock(UploadLimits.class);
    private final DocumentProperties properties = new DocumentProperties("award-documents",
        DataSize.ofMegabytes(10), LIMIT, null, DataSize.ofMegabytes(50), LIMIT, null, null);
    private final DocumentUpload upload = new DocumentUpload(documents,
        new AwardOwnership(awards, mock(UserRepository.class), access), new DocumentContent(), storage, properties,
        events, new TransactionTemplate(mock(PlatformTransactionManager.class)), malware, limits, access);
    private final Organization department = TestUsers.organization(64L, OrganizationType.DEPARTMENT);
    private final User owner = TestUsers.person(OWNER_ID, "owner@chnu.edu.ua", department);

    @BeforeEach
    void setUp() {
        when(access.callerId()).thenReturn(OWNER_ID);
        when(storage.bucket()).thenReturn("award-documents");
        when(documents.findFirstByAwardIdAndChecksum(anyLong(), anyString())).thenReturn(Optional.empty());
        when(documents.saveAndFlush(any())).thenAnswer(invocation -> {
            Document document = invocation.getArgument(0);
            ReflectionTestUtils.setField(document, "id", DOCUMENT_ID);
            return document;
        });
    }

    @Test
    void ac1_1_theObjectIsWrittenUnderARandomKeyBeforeTheRowAndTheRowTakesItsFactsFromTheContent() {
        draft(AwardStatus.DRAFT, owner);

        DocumentResponse response = upload.upload(AWARD_ID, file("C:\\scans\\диплом.pdf", PDF),
            DocumentType.CERTIFICATE, "  Скан диплома  ");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).put(key.capture(), any(), eq((long) PDF.length), eq("application/pdf"));
        assertThat(key.getValue()).matches("awards/" + AWARD_ID + "/[0-9a-f-]{36}");
        verify(events).publishEvent(new ObjectStored(key.getValue()));
        ArgumentCaptor<Document> row = ArgumentCaptor.forClass(Document.class);
        verify(documents).saveAndFlush(row.capture());
        Document saved = row.getValue();
        assertThat(saved.getStorageKey()).isEqualTo(key.getValue());
        assertThat(saved.getStorageBucket()).isEqualTo("award-documents");
        assertThat(saved.getFormat()).isEqualTo(DocumentFormat.PDF);
        assertThat(saved.getChecksum()).hasSize(64);
        assertThat(saved.getUploadedBy()).isSameAs(owner);
        assertThat(response.id()).isEqualTo(DOCUMENT_ID);
        assertThat(response.fileName()).isEqualTo("диплом.pdf");
        assertThat(response.mimeType()).isEqualTo("application/pdf");
        assertThat(response.size()).isEqualTo(PDF.length);
        assertThat(response.description()).isEqualTo("Скан диплома");
        assertThat(response.uploadedBy().id()).isEqualTo(OWNER_ID);
    }

    @Test
    void ac1_6_aBlankDescriptionIsNoneAndALongOneIsRefused() {
        draft(AwardStatus.DRAFT, owner);

        assertThat(upload.upload(AWARD_ID, file("a.pdf", PDF), DocumentType.PHOTO, "   ").description()).isNull();
        assertProblem(() -> upload.upload(AWARD_ID, file("b.pdf", PDF), DocumentType.PHOTO, "x".repeat(501)),
            "invalid-parameter");
    }

    @Test
    void ac1_2_anEmptyOrOversizedFileIsRefusedBeforeAnythingIsStored() {
        draft(AwardStatus.DRAFT, owner);
        byte[] oversized = new byte[(int) DataSize.ofMegabytes(10).toBytes() + 1];
        System.arraycopy(PDF, 0, oversized, 0, PDF.length);

        assertProblem(() -> upload.upload(AWARD_ID, file("e.pdf", new byte[0]), DocumentType.PHOTO, null),
            "empty-file");
        assertProblem(() -> upload.upload(AWARD_ID, file("big.pdf", oversized), DocumentType.PHOTO, null),
            "file-too-large");
        verifyNoInteractions(storage, events);
    }

    @Test
    void ac1_3_contentOfAnotherTypeOrAMismatchedExtensionIsRefused() {
        draft(AwardStatus.DRAFT, owner);
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0x0D};

        assertProblem(() -> upload.upload(AWARD_ID, file("a.docx", "PK\u0003\u0004".getBytes(
            StandardCharsets.ISO_8859_1)), DocumentType.PHOTO, null), "unsupported-type");
        assertProblem(() -> upload.upload(AWARD_ID, file("scan.pdf", png), DocumentType.PHOTO, null),
            "content-mismatch");
        verify(storage, never()).put(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void ac1_4_someoneElsesAwardIsMissingAndASubmittedOneIsNotEditable() {
        draft(AwardStatus.DRAFT, TestUsers.person(99L, "other@chnu.edu.ua", department));
        assertThatThrownBy(() -> upload.upload(AWARD_ID, file("a.pdf", PDF), DocumentType.PHOTO, null))
            .isInstanceOf(AwardNotFoundException.class);

        draft(AwardStatus.PENDING, owner);
        assertProblem(() -> upload.upload(AWARD_ID, file("a.pdf", PDF), DocumentType.PHOTO, null),
            "award-not-editable");
        verifyNoInteractions(storage);
    }

    @Test
    void ac1_5_theEleventhDocumentAndASecondCopyAreRefused() {
        draft(AwardStatus.DRAFT, owner);
        when(documents.countByAwardId(AWARD_ID)).thenReturn((long) LIMIT);
        assertProblem(() -> upload.upload(AWARD_ID, file("a.pdf", PDF), DocumentType.PHOTO, null),
            "document-limit");

        when(documents.countByAwardId(AWARD_ID)).thenReturn(1L);
        Document existing = Document.builder().build();
        ReflectionTestUtils.setField(existing, "id", 3L);
        when(documents.findFirstByAwardIdAndChecksum(eq(AWARD_ID), anyString())).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> upload.upload(AWARD_ID, file("a.pdf", PDF), DocumentType.PHOTO, null))
            .hasFieldOrPropertyWithValue(TYPE, "duplicate-document")
            .extracting("properties").isEqualTo(Map.of("documentId", 3L));
        verifyNoInteractions(storage);
    }

    @Test
    void ac1_11_anUnavailableStorageWritesNoRow() {
        draft(AwardStatus.DRAFT, owner);
        doThrow(new StorageUnavailableException(new IllegalStateException("down")))
            .when(storage).put(anyString(), any(), anyLong(), anyString());

        assertProblem(() -> upload.upload(AWARD_ID, file("a.pdf", PDF), DocumentType.PHOTO, null),
            "storage-unavailable");
        verify(documents, never()).saveAndFlush(any());
        verifyNoInteractions(events);
    }

    @Test
    void ac1_11_aSubmissionWhileTheContentIsStoredRefusesTheRowAndReleasesTheObject() {
        Award draft = TestAwards.award(owner, department).build();
        Award submitted = TestAwards.award(owner, department).status(AwardStatus.PENDING).build();
        when(awards.findForUpdate(AWARD_ID)).thenReturn(Optional.of(draft), Optional.of(draft),
            Optional.of(submitted));

        assertProblem(() -> upload.upload(AWARD_ID, file("a.pdf", PDF), DocumentType.PHOTO, null),
            "award-not-editable");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).put(key.capture(), any(), anyLong(), anyString());
        verify(events).publishEvent(new ObjectStored(key.getValue()));
        verify(documents, never()).saveAndFlush(any());
    }

    @Test
    void ac3_1_aRefusalOfTheScreeningStoresNothing() {
        draft(AwardStatus.DRAFT, owner);
        MockMultipartFile eicar = file("eicar.pdf", "X5O!P%@AP[4".getBytes(StandardCharsets.US_ASCII));
        doThrow(new IllegalStateException("malware-detected"))
            .when(malware).check(AWARD_ID, OWNER_ID, eicar);

        assertThatThrownBy(() -> upload.upload(AWARD_ID, eicar, DocumentType.PHOTO, null))
            .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(storage, events);
        verify(documents, never()).saveAndFlush(any());
    }

    @Test
    void ac3_5_ac3_6_theRateIsCountedFirstAndTheQuotaBeforeTheScanAndAgainUnderTheLock() {
        draft(AwardStatus.DRAFT, owner);

        upload.upload(AWARD_ID, file("a.pdf", PDF), DocumentType.PHOTO, null);

        var order = inOrder(limits, malware, storage);
        order.verify(limits).checkRate(OWNER_ID);
        order.verify(limits).checkQuota(OWNER_ID, PDF.length);
        order.verify(malware).check(eq(AWARD_ID), eq(OWNER_ID), any());
        order.verify(storage).put(anyString(), any(), anyLong(), anyString());
        order.verify(limits).checkQuotaLocked(OWNER_ID, PDF.length);
    }

    @Test
    void ac3_6_aRefusedRateTouchesNeitherTheAwardNorTheScanner() {
        doThrow(new IllegalStateException("too-many-requests"))
            .when(limits).checkRate(OWNER_ID);

        assertThatThrownBy(() -> upload.upload(AWARD_ID, file("a.pdf", PDF), DocumentType.PHOTO, null))
            .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(awards, malware, storage);
    }

    private void draft(AwardStatus status, User awardOwner) {
        Award award = TestAwards.award(awardOwner, department).status(status).build();
        when(awards.findForUpdate(AWARD_ID)).thenReturn(Optional.of(award));
    }

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    private static void assertProblem(Runnable call, String type) {
        assertThatThrownBy(call::run).hasFieldOrPropertyWithValue(TYPE, type);
    }
}
