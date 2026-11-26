package ua.edu.chnu.awards.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Sheet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.RestAssured;
import io.restassured.response.Response;

import ua.edu.chnu.awards.analytics.service.UnitAggregates;
import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.AwardRows;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UnitAnalyticsExportFT extends AbstractFunctionalTest {

    private static final String UNITS = "/api/v1/analytics/units";
    private static final String INVALID = "urn:awards:problem:invalid-parameter";
    private static final String PREFIX = "ft.export.";
    private static final String DOMAIN = "@chnu.edu.ua";
    private static final String SECRETARY = PREFIX + "secretary" + DOMAIN;
    private static final String DEAN = PREFIX + "dean" + DOMAIN;
    private static final String EMPLOYEE = PREFIX + "employee" + DOMAIN;
    private static final String INJECTED = "=HYPERLINK(\"http://example.org\")";
    private static final long FACULTY_ID = 10L;
    private static final long DEPARTMENT_ID = 69L;
    private static final long OTHER_FACULTY_ID = 9L;
    private static final int CURRENT_YEAR = LocalDate.now(ZoneId.of("Europe/Kyiv")).getYear();
    private static final String COUNT_EXPORTS = "select count(*) from audit_logs where action_type = "
        + "'ANALYTICS_EXPORTED' and user_id = ?";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private CacheManager caches;

    @Autowired
    private JdbcTemplate jdbc;

    private long deanId;
    private long secretaryId;
    private String facultyCode;

    @BeforeAll
    void createAccounts() {
        Organization faculty = organizationRepository.findById(FACULTY_ID).orElseThrow();
        Organization department = organizationRepository.findById(DEPARTMENT_ID).orElseThrow();
        facultyCode = faculty.getCode();
        secretaryId = withRole(SECRETARY, department, RoleType.FACULTY_SECRETARY, faculty);
        deanId = withRole(DEAN, department, RoleType.DEAN, faculty);
        long employeeId = withRole(EMPLOYEE, department, RoleType.EMPLOYEE, department);
        jdbc.update("insert into awards (user_id, organization_id, title, status, category_id, "
            + "awarding_organization, award_date) values (?, ?, 'Letter', 'APPROVED', 13, ?, ?)", employeeId,
            DEPARTMENT_ID, INJECTED, LocalDate.of(CURRENT_YEAR, 1, 10));
        AwardRows.award(jdbc, employeeId).unit(FACULTY_ID).status("APPROVED")
            .awardDate(LocalDate.of(CURRENT_YEAR - 1, 2, 1)).insert();
        Objects.requireNonNull(caches.getCache(UnitAggregates.CACHE)).clear();
    }

    @AfterAll
    void deleteAccounts() {
        String owners = " (select user_id from users where email_address like '" + PREFIX + "%')";
        jdbc.update("delete from awards where user_id in" + owners);
        userRepository.findAll().stream().filter(user -> user.getEmailAddress().startsWith(PREFIX))
            .forEach(userRepository::delete);
        Objects.requireNonNull(caches.getCache(UnitAggregates.CACHE)).clear();
    }

    @Test
    void ac3_1_eachFormatIsAnAttachmentWithItsMediaTypeAndNoStore() {
        String dean = tokenOf(DEAN);
        Map<String, String> types = Map.of("csv", "text/csv",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "pdf", "application/pdf");
        for (Map.Entry<String, String> type : types.entrySet()) {
            as(dean).queryParam("format", type.getKey()).queryParam("from", CURRENT_YEAR - 4)
                .get(UNITS + "/" + FACULTY_ID + "/export").then().statusCode(HttpStatus.OK.value())
                .header(HttpHeaders.CONTENT_TYPE, startsWith(type.getValue()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, equalTo("attachment; filename=\"analytics-" + facultyCode
                    + "-" + (CURRENT_YEAR - 4) + "-" + CURRENT_YEAR + "." + type.getKey() + "\""));
        }
    }

    @Test
    void ac3_1_accessFollowsTheUnitAnalytics() {
        String path = UNITS + "/" + FACULTY_ID + "/export";
        int before = exports(secretaryId);
        as(tokenOf(SECRETARY)).queryParam("format", "pdf").get(path).then()
            .statusCode(HttpStatus.NOT_FOUND.value());
        as(tokenOf(DEAN)).queryParam("format", "pdf").get(UNITS + "/" + OTHER_FACULTY_ID + "/export").then()
            .statusCode(HttpStatus.NOT_FOUND.value());
        as(tokenOf(EMPLOYEE)).queryParam("format", "pdf").get(path).then().statusCode(HttpStatus.FORBIDDEN.value());
        RestAssured.given().queryParam("format", "pdf").get(path).then()
            .statusCode(HttpStatus.UNAUTHORIZED.value());
        assertThat(exports(secretaryId)).isEqualTo(before);
    }

    @Test
    void ac3_1_unknownFormatLanguageOrYearNameTheParameter() {
        String dean = tokenOf(DEAN);
        String path = UNITS + "/" + FACULTY_ID + "/export";
        int before = exports(deanId);
        as(dean).queryParam("format", "docx").get(path).then().statusCode(HttpStatus.BAD_REQUEST.value())
            .body("type", equalTo(INVALID)).body("parameter", equalTo("format"));
        as(dean).get(path).then().statusCode(HttpStatus.BAD_REQUEST.value()).body("parameter", equalTo("format"));
        as(dean).queryParam("format", "csv").queryParam("lang", "de").get(path).then()
            .statusCode(HttpStatus.BAD_REQUEST.value()).body("type", equalTo(INVALID))
            .body("parameter", equalTo("lang"));
        as(dean).queryParam("format", "csv").queryParam("to", 2101).get(path).then()
            .statusCode(HttpStatus.BAD_REQUEST.value()).body("parameter", equalTo("to"));
        assertThat(exports(deanId)).isEqualTo(before);
    }

    @Test
    void ac3_3_ac3_5_theCsvDefusesFormulasAndTheExportIsAudited() {
        int before = exports(deanId);

        byte[] csv = download("csv", "en");

        String text = new String(csv, StandardCharsets.UTF_8);
        assertThat(text).startsWith("﻿section,key,label,year,current,previous\r\n")
            .contains(",\"'=HYPERLINK(\"\"http://example.org\"\")\",,1,");
        assertThat(exports(deanId)).isEqualTo(before + 1);
        Map<String, Object> row = jdbc.queryForMap("select entity_type, entity_id, new_values::text as details "
            + "from audit_logs where action_type = 'ANALYTICS_EXPORTED' and user_id = ? order by log_id desc limit 1",
            deanId);
        assertThat(row).containsEntry("entity_type", "ANALYTICS").containsEntry("entity_id", FACULTY_ID);
        assertThat((String) row.get("details")).contains("\"format\": \"csv\"", "\"language\": \"en\"",
            "\"from\": " + (CURRENT_YEAR - 4), "\"to\": " + CURRENT_YEAR, "\"peopleNamed\": 0");
    }

    @Test
    void ac3_2_ac3_4_thePdfAndTheWorkbookCarryTheSectionsInUkrainian() throws IOException {
        try (PDDocument pdf = Loader.loadPDF(download("pdf", "uk"))) {
            String text = new PDFTextStripper().getText(pdf);

            assertThat(text).contains("Аналітика підрозділу", "Порівняння кафедр", "Сторінка 1 з");
            assertThat(pdf.getNumberOfPages()).isBetween(1, 6);
        }
        try (ReadableWorkbook workbook = new ReadableWorkbook(new ByteArrayInputStream(download("xlsx", "uk")))) {
            List<String> sheets = workbook.getSheets().map(Sheet::getName).toList();

            assertThat(sheets).hasSize(9).contains("Підсумок", "Порівняння кафедр", "Що вирізняється");
        }
    }

    private byte[] download(String format, String lang) {
        Response response = as(tokenOf(DEAN)).queryParam("format", format).queryParam("lang", lang)
            .get(UNITS + "/" + FACULTY_ID + "/export");
        response.then().statusCode(HttpStatus.OK.value());
        return response.asByteArray();
    }

    private int exports(long userId) {
        return Objects.requireNonNull(jdbc.queryForObject(COUNT_EXPORTS, Integer.class, userId));
    }
}
