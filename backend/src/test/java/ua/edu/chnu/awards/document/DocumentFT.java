package ua.edu.chnu.awards.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.http.entity.mime.HttpMultipartMode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import ua.edu.chnu.awards.document.service.UploadLimits;
import ua.edu.chnu.awards.gdpr.service.DataExportService;
import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.AwardApi;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentFT extends AbstractFunctionalTest {

    private static final String EMPLOYEE = "ft.doc.employee@chnu.edu.ua";
    private static final String COLLEAGUE = "ft.doc.colleague@chnu.edu.ua";
    private static final String DEAN = "ft.doc.dean@chnu.edu.ua";
    private static final String OUTSIDER = "ft.doc.outsider@chnu.edu.ua";
    private static final String ADMIN = "ft.doc.admin@chnu.edu.ua";
    private static final String RACER = "ft.doc.racer@chnu.edu.ua";
    private static final List<String> ACCOUNTS = List.of(EMPLOYEE, COLLEAGUE, DEAN, OUTSIDER, ADMIN, RACER);
    private static final String DOCUMENTS = "/api/v1/documents/";
    private static final String TYPE = "type";
    private static final String PROBLEM = "urn:awards:problem:";
    private static final String FILE = "file";
    private static final String PHOTO = "PHOTO";
    private static final long OTHER_FACULTY_ID = 10L;
    private static final int TEN_MB = 10 * 1024 * 1024;
    private static final int LIMIT = 10;
    private static final long FIVE_SECONDS = 5000L;
    private static final int QUOTA_FILES = 5;
    private static final int ROOM = 100;
    private static final long SECONDS_PER_MINUTE = 60;
    private static final RestAssuredConfig BROWSER_MULTIPART = RestAssuredConfig.config()
        .httpClient(HttpClientConfig.httpClientConfig().httpMultipartMode(HttpMultipartMode.BROWSER_COMPATIBLE));

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbc;

    private final Random random = new Random(42);
    private String employee;
    private long employeeId;

    @BeforeAll
    void createUsers() {
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        employeeId = withRole(EMPLOYEE, department, RoleType.EMPLOYEE, department);
        withRole(COLLEAGUE, department, RoleType.EMPLOYEE, department);
        withRole(RACER, department, RoleType.EMPLOYEE, department);
        Organization faculty = organizationRepository.findById(TestUsers.FMI_FACULTY_ID).orElseThrow();
        withRole(DEAN, faculty, RoleType.DEAN, faculty);
        Organization other = organizationRepository.findById(OTHER_FACULTY_ID).orElseThrow();
        withRole(OUTSIDER, other, RoleType.FACULTY_SECRETARY, other);
        Organization university = organizationRepository.findById(TestUsers.UNIVERSITY_ID).orElseThrow();
        withRole(ADMIN, university, RoleType.SYSTEM_ADMIN, university);
    }

    @AfterAll
    void deleteUsers() {
        jdbc.update("delete from awards where user_id in"
            + " (select user_id from users where email_address like 'ft.doc.%')");
        redis.delete(DataExportService.THROTTLE_KEY_PREFIX + employeeId);
        ACCOUNTS.forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @BeforeEach
    void setUp() {
        employee = tokenOf(EMPLOYEE);
    }

    @Test
    void ac1_1_ac1_7_theOwnerUploadsEveryFormatAndListsThemOldestFirst() {
        long award = AwardApi.complete(employee, Map.of("titleUk", "Документи всіх форматів"));

        Response created = upload(employee, award, "диплом.pdf", pdf(), "CERTIFICATE");
        created.then().statusCode(201)
            .header("Location", endsWith(DOCUMENTS + created.jsonPath().getLong("id")))
            .body("awardId", equalTo((int) award))
            .body("fileName", equalTo("диплом.pdf"))
            .body("type", equalTo("CERTIFICATE"))
            .body("mimeType", equalTo("application/pdf"))
            .body("uploadedBy.email", equalTo(EMPLOYEE));
        upload(employee, award, "photo.jpeg", jpeg(), PHOTO).then().statusCode(201)
            .body("mimeType", equalTo("image/jpeg"));
        upload(employee, award, "scan", png(), "SUPPORTING_DOCUMENT").then().statusCode(201)
            .body("fileName", equalTo("scan.png"));
        upload(employee, award, "image.webp", webp(), PHOTO).then().statusCode(201)
            .body("mimeType", equalTo("image/webp"));

        as(employee).get(AwardApi.AWARDS + "/" + award + "/documents").then().statusCode(200)
            .body("fileName", contains("диплом.pdf", "photo.jpeg", "scan.png", "image.webp"));
    }

    @Test
    void ac1_2_ac1_3_emptyOversizedAndMislabelledFilesAreRefused() {
        long award = AwardApi.complete(employee, Map.of("titleUk", "Відмови"));
        byte[] justOver = sized(TEN_MB + 1);
        byte[] png = png();

        upload(employee, award, "empty.pdf", new byte[0], PHOTO).then().statusCode(400)
            .body(TYPE, equalTo(PROBLEM + "empty-file"));
        upload(employee, award, "big.pdf", justOver, PHOTO).then().statusCode(413)
            .body(TYPE, equalTo(PROBLEM + "file-too-large"));
        upload(employee, award, "huge.pdf", sized(12 * 1024 * 1024), PHOTO).then().statusCode(413)
            .body(TYPE, equalTo(PROBLEM + "file-too-large"));
        upload(employee, award, "fake.pdf", png, PHOTO).then().statusCode(400)
            .body(TYPE, equalTo(PROBLEM + "content-mismatch"));
        upload(employee, award, "letter.docx", "PK\u0003\u0004word".getBytes(StandardCharsets.ISO_8859_1), PHOTO)
            .then().statusCode(400).body(TYPE, equalTo(PROBLEM + "unsupported-type"));

        as(employee).get(AwardApi.AWARDS + "/" + award + "/documents").then().statusCode(200).body("", hasSize(0));
    }

    @Test
    void ac1_5_theEleventhDocumentAndASecondCopyAreRefused() {
        long award = AwardApi.complete(employee, Map.of("titleUk", "Ліміт"));
        byte[] first = pdf();
        long firstId = upload(employee, award, "first.pdf", first, PHOTO).then().statusCode(201).extract()
            .jsonPath().getLong("id");

        upload(employee, award, "again.pdf", first, PHOTO).then().statusCode(409)
            .body(TYPE, equalTo(PROBLEM + "duplicate-document"))
            .body("documentId", equalTo((int) firstId));
        for (int index = 1; index < LIMIT; index++) {
            upload(employee, award, "page" + index + ".pdf", pdf(), PHOTO).then().statusCode(201);
        }
        upload(employee, award, "eleventh.pdf", pdf(), PHOTO).then().statusCode(409)
            .body(TYPE, equalTo(PROBLEM + "document-limit"));
    }

    @Test
    void ac1_5_twoUploadsOfTheSameFileAtOnceStoreOne() throws Exception {
        long award = AwardApi.complete(employee, Map.of("titleUk", "Одночасно"));
        byte[] same = pdf();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                Callable<Integer> call = () -> upload(employee, award, "same.pdf", same, PHOTO).statusCode();
                results.add(pool.submit(call));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void ac1_4_ac1_7_ac1_8_ac1_9_readersOfTheAwardDownloadAndOnlyTheOwnerChangesADraft() {
        long award = AwardApi.complete(employee, Map.of("titleUk", "Доступ до документів"));
        byte[] content = pdf();
        long document = upload(employee, award, "грамота 2025.pdf", content, "CERTIFICATE").then().statusCode(201)
            .extract().jsonPath().getLong("id");
        String dean = tokenOf(DEAN);
        String outsider = tokenOf(OUTSIDER);
        String colleague = tokenOf(COLLEAGUE);

        as(dean).get(AwardApi.AWARDS + "/" + award + "/documents").then().statusCode(404);
        as(dean).get(DOCUMENTS + document).then().statusCode(404);
        upload(colleague, award, "x.pdf", pdf(), PHOTO).then().statusCode(404);
        as(colleague).delete(DOCUMENTS + document).then().statusCode(404);
        upload(tokenOf(ADMIN), award, "x.pdf", pdf(), PHOTO).then().statusCode(403);
        as(employee).get("/api/v1/documents/abc").then().statusCode(400);

        AwardApi.submit(employee, award, AwardApi.version(employee, award), true).then().statusCode(200);

        Response download = as(dean).get(DOCUMENTS + document);
        download.then().statusCode(200)
            .header("Content-Type", "application/pdf")
            .header("Content-Disposition", containsString("; filename*=UTF-8''"
                + "%D0%B3%D1%80%D0%B0%D0%BC%D0%BE%D1%82%D0%B0%202025.pdf"))
            .header("X-Content-Type-Options", "nosniff")
            .header("Cache-Control", "no-store, private")
            .header("Content-Security-Policy", "sandbox");
        assertThat(download.asByteArray()).isEqualTo(content);
        as(dean).get(AwardApi.AWARDS + "/" + award + "/documents").then().statusCode(200)
            .body("id", contains((int) document));
        as(outsider).get(AwardApi.AWARDS + "/" + award + "/documents").then().statusCode(404);
        as(outsider).get(DOCUMENTS + document).then().statusCode(404);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action_type = 'DOCUMENT_DOWNLOAD' "
            + "and entity_id = ? and user_id = (select user_id from users where email_address = ?)", Long.class,
            document, DEAN)).isEqualTo(1L);

        upload(employee, award, "late.pdf", pdf(), PHOTO).then().statusCode(409)
            .body(TYPE, equalTo(PROBLEM + "award-not-editable"));
        as(employee).delete(DOCUMENTS + document).then().statusCode(409)
            .body(TYPE, equalTo(PROBLEM + "award-not-editable"));
    }

    @Test
    void ac1_9_theOwnerDeletesADocumentOfTheDraft() {
        long award = AwardApi.complete(employee, Map.of("titleUk", "Видалення документа"));
        long document = upload(employee, award, "wrong.pdf", pdf(), PHOTO).then().statusCode(201).extract()
            .jsonPath().getLong("id");

        as(employee).delete(DOCUMENTS + document).then().statusCode(204);

        as(employee).get(DOCUMENTS + document).then().statusCode(404);
        as(employee).delete(DOCUMENTS + document).then().statusCode(404);
    }

    @Test
    void ac1_13_theExportLinkDownloadsTheFileForTheOwner() {
        long award = AwardApi.complete(employee, Map.of("titleUk", "Експорт документів"));
        byte[] content = pdf();
        upload(employee, award, "export.pdf", content, PHOTO).then().statusCode(201);
        redis.delete(DataExportService.THROTTLE_KEY_PREFIX + employeeId);

        List<String> paths = as(employee).get("/api/v1/users/me/export").then().statusCode(200).extract()
            .jsonPath().getList("documents.api_path");

        assertThat(paths).isNotEmpty();
        List<byte[]> downloads = paths.stream()
            .map(path -> as(employee).get(path).then().statusCode(200).extract().asByteArray()).toList();
        assertThat(downloads).anySatisfy(bytes -> assertThat(bytes).isEqualTo(content));
    }

    @Test
    void ac1_15_aTenMegabyteUploadAnswersWithinFiveSeconds() {
        long award = AwardApi.complete(employee, Map.of("titleUk", "Великий файл"));
        byte[] large = sized(TEN_MB);

        long started = System.currentTimeMillis();
        upload(employee, award, "large.pdf", large, "SUPPORTING_DOCUMENT").then().statusCode(201)
            .body("size", equalTo(TEN_MB));
        long elapsed = System.currentTimeMillis() - started;

        assertThat(elapsed).isLessThan(FIVE_SECONDS);
    }

    @Test
    void ac3_5_aFileBeyondTheUsersQuotaIsRefused() {
        String colleague = tokenOf(COLLEAGUE);
        long award = AwardApi.complete(colleague, Map.of("titleUk", "Квота"));
        for (int i = 0; i < QUOTA_FILES; i++) {
            long id = upload(colleague, award, "page" + i + ".pdf", pdf(), PHOTO).then().statusCode(201)
                .extract().jsonPath().getLong("id");
            jdbc.update("update documents set file_size = ? where document_id = ?", TEN_MB, id);
        }

        upload(colleague, award, "one-more.pdf", pdf(), PHOTO).then().statusCode(409)
            .body(TYPE, equalTo(PROBLEM + "storage-quota"))
            .body("quota", equalTo(QUOTA_FILES * TEN_MB));
    }

    @Test
    void ac3_5_uploadsToTwoDraftsAtOnceCannotBothPassTheQuota() throws Exception {
        String racer = tokenOf(RACER);
        long first = AwardApi.complete(racer, Map.of("titleUk", "Перша квота"));
        long second = AwardApi.complete(racer, Map.of("titleUk", "Друга квота"));
        for (int i = 0; i < QUOTA_FILES; i++) {
            long id = upload(racer, first, "page" + i + ".pdf", pdf(), PHOTO).then().statusCode(201)
                .extract().jsonPath().getLong("id");
            jdbc.update("update documents set file_size = ? where document_id = ?",
                i == 0 ? TEN_MB - ROOM : TEN_MB, id);
        }

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            byte[] one = pdf();
            byte[] other = pdf();
            Future<Integer> toFirst = pool.submit(() -> upload(racer, first, "a.pdf", one, PHOTO).statusCode());
            Future<Integer> toSecond = pool.submit(() -> upload(racer, second, "b.pdf", other, PHOTO).statusCode());
            assertThat(List.of(toFirst.get(), toSecond.get())).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void ac3_6_anUploadBeyondTheRateIsRefusedWithRetryAfter() {
        long award = AwardApi.complete(employee, Map.of("titleUk", "Частота"));
        long window = Instant.now().getEpochSecond() / SECONDS_PER_MINUTE;
        List<String> keys = List.of(UploadLimits.RATE_KEY_PREFIX + employeeId + ":" + window,
            UploadLimits.RATE_KEY_PREFIX + employeeId + ":" + (window + 1));
        keys.forEach(key -> redis.opsForValue().set(key, String.valueOf(Integer.MAX_VALUE - 1)));
        try {
            upload(employee, award, "fast.pdf", pdf(), PHOTO).then().statusCode(429)
                .header("Retry-After", matchesPattern("\\d+"))
                .body(TYPE, equalTo(PROBLEM + "too-many-requests"));
        } finally {
            redis.delete(keys);
        }
    }

    private static Response upload(String token, long award, String name, byte[] content, String type) {
        RequestSpecification request = as(token).config(BROWSER_MULTIPART)
            .contentType("multipart/form-data; charset=UTF-8")
            .multiPart(FILE, name, content, "application/octet-stream");
        return request.multiPart(TYPE, type).post(AwardApi.AWARDS + "/" + award + "/documents");
    }

    private byte[] pdf() {
        return withHead("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII), 64);
    }

    private byte[] jpeg() {
        return withHead(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}, 64);
    }

    private byte[] png() {
        return withHead(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}, 64);
    }

    private byte[] webp() {
        return withHead(new byte[] {'R', 'I', 'F', 'F', '$', 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '}, 64);
    }

    private byte[] sized(int size) {
        return withHead("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII), size);
    }

    private byte[] withHead(byte[] head, int size) {
        byte[] content = new byte[size];
        random.nextBytes(content);
        System.arraycopy(head, 0, content, 0, head.length);
        return content;
    }
}
