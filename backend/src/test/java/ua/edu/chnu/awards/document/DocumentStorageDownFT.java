package ua.edu.chnu.awards.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static ua.edu.chnu.awards.common.web.ApiExceptionHandler.TYPE_PREFIX;
import static ua.edu.chnu.awards.support.DocumentApi.DOCUMENTS;
import static ua.edu.chnu.awards.support.DocumentApi.upload;
import static ua.edu.chnu.awards.support.DocumentTestConstants.PDF;

import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MinIOContainer;

import io.restassured.response.Response;

import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.AwardApi;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;

import software.amazon.awssdk.services.s3.S3Client;

/**
 * The document endpoints while the object storage does not answer: the storage container is paused, so that
 * calls run into the call timeout, and resumed after each test.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = "app.documents.storage.call-timeout=2s")
class DocumentStorageDownFT extends AbstractFunctionalTest {

    private static final String OWNER = "ft.down.owner@chnu.edu.ua";
    private static final String BUCKET = "award-documents";
    private static final String UNAVAILABLE = TYPE_PREFIX + "storage-unavailable";
    private static final String TYPE = "type";

    @Autowired
    private MinIOContainer minioContainer;

    @Autowired
    private S3Client s3;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    private String owner;

    @BeforeAll
    void createOwner() {
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        withRole(OWNER, department, RoleType.EMPLOYEE, department);
    }

    @AfterAll
    void deleteOwner() {
        jdbc.update("delete from awards where user_id in (select user_id from users where email_address = ?)",
            OWNER);
        userRepository.findByEmailAddressIgnoreCase(OWNER).ifPresent(userRepository::delete);
    }

    @BeforeEach
    void signIn() {
        owner = tokenOf(OWNER);
    }

    @Test
    void f4_anUploadWhileTheStorageIsDownAnswers503AndStoresNoRow() {
        long award = AwardApi.complete(owner, Map.of("titleUk", "Сховище недоступне"));

        Response refused = whileStorageIsDown(() -> upload(owner, award, "диплом.pdf", PDF, "CERTIFICATE"));

        refused.then().statusCode(HttpStatus.SERVICE_UNAVAILABLE.value()).body(TYPE, equalTo(UNAVAILABLE));
        assertThat(jdbc.queryForObject("select count(*) from documents where award_id = ?", Long.class, award))
            .isZero();
    }

    @Test
    void f4_aDownloadWhileTheStorageIsDownAnswers503AndIsNotRecorded() {
        long document = stored("Завантаження без сховища");

        Response refused = whileStorageIsDown(() -> as(owner).get(DOCUMENTS + document));

        refused.then().statusCode(HttpStatus.SERVICE_UNAVAILABLE.value()).body(TYPE, equalTo(UNAVAILABLE));
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action_type = 'DOCUMENT_DOWNLOAD'"
            + " and entity_type = 'documents' and entity_id = ?", Long.class, document)).isZero();
    }

    @Test
    void f4_aDeletionWhileTheStorageIsDownSucceedsAndLeavesTheObjectForTheSweep() {
        long document = stored("Видалення без сховища");
        String key = jdbc.queryForObject("select storage_key from documents where document_id = ?", String.class,
            document);

        Response deleted = whileStorageIsDown(() -> as(owner).delete(DOCUMENTS + document));

        deleted.then().statusCode(HttpStatus.NO_CONTENT.value());
        assertThat(jdbc.queryForObject("select count(*) from documents where document_id = ?", Long.class,
            document)).isZero();
        assertThat(s3.headObject(request -> request.bucket(BUCKET).key(key)).contentLength())
            .isEqualTo(PDF.length);
    }

    private long stored(String title) {
        long award = AwardApi.complete(owner, Map.of("titleUk", title));
        return upload(owner, award, "диплом.pdf", PDF, "CERTIFICATE").then().statusCode(HttpStatus.CREATED.value())
            .extract().jsonPath().getLong("id");
    }

    private Response whileStorageIsDown(Supplier<Response> call) {
        String container = minioContainer.getContainerId();
        DockerClientFactory.instance().client().pauseContainerCmd(container).exec();
        try {
            return call.get();
        } finally {
            DockerClientFactory.instance().client().unpauseContainerCmd(container).exec();
        }
    }
}
