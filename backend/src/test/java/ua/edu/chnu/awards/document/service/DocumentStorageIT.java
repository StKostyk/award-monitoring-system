package ua.edu.chnu.awards.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ua.edu.chnu.awards.support.DocumentTestConstants.PDF;
import static ua.edu.chnu.awards.support.DocumentTestConstants.file;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import ua.edu.chnu.awards.award.dto.AwardForm;
import ua.edu.chnu.awards.award.service.AwardService;
import ua.edu.chnu.awards.config.DocumentProperties;
import ua.edu.chnu.awards.document.dto.DocumentResponse;
import ua.edu.chnu.awards.document.entity.DocumentType;
import ua.edu.chnu.awards.document.repository.DocumentRepository;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;

import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

class DocumentStorageIT extends AbstractIntegrationTest {

    private static final String OWNER = "it.documents@chnu.edu.ua";
    private static final String BUCKET = "award-documents";
    private static final byte[] OTHER_PDF = "%PDF-1.4 second page".getBytes(StandardCharsets.US_ASCII);
    private static final String AUDIT_ROWS = "select action_type, user_id from audit_logs "
        + "where entity_type = 'documents' and entity_id = ? order by log_id";

    @Autowired
    private DocumentUpload upload;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private AwardService awardService;

    @Autowired
    private DocumentSweeper sweeper;

    @Autowired
    private ObjectStorage storage;

    @Autowired
    private DocumentRepository documents;

    @Autowired
    private DocumentProperties properties;

    @Autowired
    private S3Client s3;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private User owner;
    private long awardId;

    @BeforeEach
    void setUp() {
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        owner = userRepository.save(TestUsers.user(OWNER, department));
        TestUsers.signInAs(owner);
        awardId = awardService.create(new AwardForm("Letter", null, null, null, null, null, null, null, null,
            null)).id();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        jdbc.update("delete from awards where user_id = ?", owner.getId());
        userRepository.findByEmailAddressIgnoreCase(OWNER).ifPresent(userRepository::delete);
    }

    @Test
    void ac1_1_ac1_12_ac1_14_theContentIsEncryptedInAPrivateBucketAndTheRowNamesTheUploader() {
        DocumentResponse stored = upload.upload(awardId, file("диплом.pdf", PDF), DocumentType.CERTIFICATE, null);

        Map<String, Object> row = jdbc.queryForMap("select storage_bucket, storage_key, processing_status, "
            + "storage_url, file_type, mime_type, document_type from documents where document_id = ?", stored.id());
        assertThat(row).containsEntry("storage_bucket", BUCKET).containsEntry("processing_status", "PENDING")
            .containsEntry("file_type", "PDF").containsEntry("mime_type", "application/pdf")
            .containsEntry("document_type", "CERTIFICATE").containsEntry("storage_url", null);
        String key = (String) row.get("storage_key");
        assertThat(key).matches("awards/" + awardId + "/[0-9a-f-]{36}");
        assertThat(s3.getObjectAsBytes(request -> request.bucket(BUCKET).key(key)).asByteArray()).isEqualTo(PDF);
        assertThat(s3.headObject(request -> request.bucket(BUCKET).key(key)).serverSideEncryption())
            .isEqualTo(ServerSideEncryption.AES256);
        assertThat(s3.getBucketEncryption(request -> request.bucket(BUCKET)).serverSideEncryptionConfiguration()
            .rules().getFirst().applyServerSideEncryptionByDefault().sseAlgorithm())
            .isEqualTo(ServerSideEncryption.AES256);
        assertThatThrownBy(() -> s3.getBucketPolicy(request -> request.bucket(BUCKET)))
            .isInstanceOf(S3Exception.class);
        try (S3Client anonymous = anonymous()) {
            assertThatThrownBy(() -> anonymous.getObject(request -> request.bucket(BUCKET).key(key)))
                .isInstanceOf(S3Exception.class);
        }
        assertThat(jdbc.queryForList(AUDIT_ROWS, stored.id()))
            .containsExactly(Map.of("action_type", "INSERT", "user_id", owner.getId()));
    }

    @Test
    void ac1_8_aDownloadStreamsTheContentAndIsRecorded() throws Exception {
        DocumentResponse stored = upload.upload(awardId, file("scan.pdf", PDF), DocumentType.CERTIFICATE, null);

        var download = documentService.open(stored.id());

        try (var content = download.content()) {
            assertThat(content.readAllBytes()).isEqualTo(PDF);
        }
        Map<String, Object> audit = jdbc.queryForMap("select user_id, entity_type, new_values->>'awardId' as award "
            + "from audit_logs where action_type = 'DOCUMENT_DOWNLOAD' and entity_id = ?", stored.id());
        assertThat(audit).containsEntry("user_id", owner.getId()).containsEntry("entity_type", "documents")
            .containsEntry("award", String.valueOf(awardId));
    }

    @Test
    void ac1_9_ac1_14_deletingADocumentRemovesItsObjectAfterTheCommitAndNamesTheCaller() {
        DocumentResponse stored = upload.upload(awardId, file("scan.pdf", PDF), DocumentType.CERTIFICATE, null);
        String key = documents.findById(stored.id()).orElseThrow().getStorageKey();

        documentService.delete(stored.id());

        assertThat(exists(key)).isFalse();
        assertThat(jdbc.queryForList(AUDIT_ROWS, stored.id())).extracting(row -> row.get("action_type"))
            .containsExactly("INSERT", "DELETE");
        assertThat(jdbc.queryForList(AUDIT_ROWS, stored.id())).extracting(row -> row.get("user_id"))
            .containsOnly(owner.getId());
    }

    @Test
    void ac1_10_deletingTheDraftRemovesTheObjectsOfAllItsDocuments() {
        upload.upload(awardId, file("a.pdf", PDF), DocumentType.CERTIFICATE, null);
        upload.upload(awardId, file("b.pdf", OTHER_PDF), DocumentType.SUPPORTING_DOCUMENT, null);
        List<String> keys = documents.storageKeysOfAward(awardId);
        assertThat(keys).hasSize(2).allMatch(this::exists);

        awardService.delete(awardId);

        assertThat(keys).noneMatch(this::exists);
        assertThat(documents.storageKeysOfAward(awardId)).isEmpty();
    }

    @Test
    void ac1_11_anUploadWhoseTransactionRollsBackLeavesNoObject() {
        transactions.executeWithoutResult(status -> {
            upload.upload(awardId, file("a.pdf", PDF), DocumentType.CERTIFICATE, null);
            status.setRollbackOnly();
        });

        assertThat(documents.storageKeysOfAward(awardId)).isEmpty();
        assertThat(s3.listObjectsV2(request -> request.bucket(BUCKET).prefix("awards/" + awardId + "/")).contents())
            .isEmpty();
    }

    @Test
    void ac1_11_theSweepRemovesOldOrphansAndKeepsDocumentsAndYoungObjects() {
        DocumentResponse stored = upload.upload(awardId, file("a.pdf", PDF), DocumentType.CERTIFICATE, null);
        final String documentKey = documents.findById(stored.id()).orElseThrow().getStorageKey();
        String orphan = "awards/999999/orphan-" + awardId;
        s3.putObject(request -> request.bucket(BUCKET).key(orphan), RequestBody.fromBytes(OTHER_PDF));

        sweeper.sweep();
        assertThat(exists(orphan)).isTrue();

        DocumentSweeper later = new DocumentSweeper(storage, documents, properties,
            Clock.offset(Clock.systemUTC(), Duration.ofHours(25)));
        assertThat(later.sweep()).isPositive();
        assertThat(exists(orphan)).isFalse();
        assertThat(exists(documentKey)).isTrue();
    }

    @Test
    void aMissingObjectIsEmpty() {
        assertThat(storage.get("awards/0/none")).isEmpty();
    }

    private boolean exists(String key) {
        try {
            s3.headObject(request -> request.bucket(BUCKET).key(key));
            return true;
        } catch (NoSuchKeyException missing) {
            return false;
        }
    }

    private S3Client anonymous() {
        return S3Client.builder().endpointOverride(properties.storage().endpoint()).region(Region.US_EAST_1)
            .credentialsProvider(AnonymousCredentialsProvider.create()).forcePathStyle(true).build();
    }

}
