package ua.edu.chnu.awards.award;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.AwardApi;
import ua.edu.chnu.awards.support.TestUsers;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AwardImportFT extends AbstractFunctionalTest {

    private static final String SECRETARY = "ft.import.secretary@chnu.edu.ua";
    private static final String EMPLOYEE = "ft.import.employee@chnu.edu.ua";
    private static final String SELF_SECRETARY = "ft.import.self@chnu.edu.ua";
    private static final long DDE_DEPARTMENT_ID = 65L;
    private static final long OTHER_FACULTY_ID = 10L;
    private static final int MAX_ROWS = 1000;
    private static final String PREVIEW = "/api/v1/award-imports/preview";
    private static final String IMPORTS = "/api/v1/award-imports";
    private static final String PROBLEM = "urn:awards:problem:";
    private static final String TYPE = "type";
    private static final String HEADER = "ПІБ;Кафедра;Назва нагороди;Дата нагородження;Рівень;Ким видано\n";
    private static final String IMPORTED = "select count(*) from awards where source = 'IMPORT' and user_id = ?";

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    private long employeeId;

    @BeforeAll
    void createUsers() {
        Organization faculty = organizationRepository.findById(TestUsers.FMI_FACULTY_ID).orElseThrow();
        Organization department = organizationRepository.findById(DDE_DEPARTMENT_ID).orElseThrow();
        withRole(SECRETARY, faculty, RoleType.FACULTY_SECRETARY, faculty);
        employeeId = withRole(EMPLOYEE, department, RoleType.EMPLOYEE, department);
        jdbc.update("update users set first_name = 'Олена', last_name = 'Імпортенко' where user_id = ?",
            employeeId);
        long self = withRole(SELF_SECRETARY, department, RoleType.FACULTY_SECRETARY, faculty);
        jdbc.update("update users set first_name = 'Ганна', last_name = 'Самоімпорт' where user_id = ?", self);
    }

    @AfterAll
    void deleteUsers() {
        String ours = " (select user_id from users where email_address like 'ft.import.%')";
        String owners = " (select user_id from users where email_address like 'ft.import.%' or added_by_id in" + ours
            + ")";
        jdbc.update("delete from awards where user_id in" + owners);
        jdbc.update("delete from users where added_by_id in" + ours);
        jdbc.update("delete from user_roles where user_id in" + ours);
        jdbc.update("delete from users where email_address like 'ft.import.%'");
    }

    @Test
    void ac5_1_preview_guesses_the_columns_matches_people_and_stores_nothing() {
        String csv = HEADER
            + "Імпортенко Олена Петрівна;Кафедра диференціальних рівнянь;Грамота МОН;17.05.2024;всеукраїнський;МОН\n"
            + "Переглядайко Василь;DDE;Подяка ректора;2023;університетський;\n"
            + "Музиченко Ігор;Кафедра музики;Подяка;2023;університетський;\n"
            + "Переглядайко Василь;DDE;Подяка декана;01.01.2099;факультетський;\n"
            + "Переглядайко Василь;DDE;;2022;невідомий;\n";
        long usersBefore = count("select count(*) from users");

        JsonPath preview = preview(csv, null).then().statusCode(HttpStatus.OK.value()).extract().jsonPath();

        assertThat(preview.getMap("mapping.columns")).containsEntry("FULL_NAME", 0).containsEntry("DEPARTMENT", 1)
            .containsEntry("TITLE", 2).containsEntry("DATE", 3).containsEntry("LEVEL", 4)
            .containsEntry("AWARDING_ORGANIZATION", 5);
        assertThat(preview.getString("checksum")).hasSize(64);
        assertThat(preview.getLong("rows[0].person.id")).isEqualTo(employeeId);
        assertThat(preview.getString("rows[0].category.level")).isEqualTo("NATIONAL");
        assertThat(preview.getString("rows[0].awardDate")).isEqualTo("2024-05-17");
        assertThat(preview.getList("rows[0].problems")).isEmpty();
        assertThat(preview.getString("rows[1].person.id")).isNull();
        assertThat(preview.getString("rows[1].person.lastName")).isEqualTo("Переглядайко");
        assertThat(codes(preview, 1)).containsExactly("awarding-organization-missing", "person-added");
        assertThat(codes(preview, 2)).contains("department-out-of-scope");
        assertThat(codes(preview, 3)).contains("date-future");
        assertThat(codes(preview, 4)).contains("title-missing", "level-unknown");
        assertThat(preview.getBoolean("rows[4].problems.find { it.code == 'title-missing' }.blocking")).isTrue();
        assertThat(count("select count(*) from users")).isEqualTo(usersBefore);
        assertThat(count("select count(*) from awards where title_uk = 'Грамота МОН'")).isZero();
    }

    @Test
    void ac5_2_import_adds_people_and_approved_awards_once() throws JsonProcessingException {
        String csv = HEADER
            + "Імпортенко Олена;DDE;Почесна грамота обласної ради;12.03.2021;обласний;Обласна рада\n"
            + "Новоімпортенко Тарас;DDE;Подяка МОН;2020;всеукраїнський;МОН України\n"
            + "Новоімпортенко Тарас;DDE;Грамота факультету;05.2019;факультетський;ФМІ\n"
            + "Імпортенко Олена;DDE;Почесна грамота обласної ради;12.03.2021;обласний;Обласна рада\n";
        JsonPath preview = preview(csv, null).then().statusCode(HttpStatus.OK.value()).extract().jsonPath();
        assertThat(codes(preview, 3)).containsExactly("row-repeated");

        run(csv, mapping(preview), preview.getString("checksum")).then().statusCode(HttpStatus.CREATED.value())
            .body("imported", equalTo(3)).body("peopleAdded", equalTo(1)).body("skipped", equalTo(1));

        Map<String, Object> person = jdbc.queryForMap("select user_id, account_status, email_address, added_by_id "
            + "from users where last_name = 'Новоімпортенко'");
        assertThat(person).containsEntry("account_status", "UNCLAIMED");
        assertThat((String) person.get("email_address")).startsWith("unclaimed-");
        long personId = ((Number) person.get("user_id")).longValue();
        assertThat(count(IMPORTED, personId)).isEqualTo(2);
        Map<String, Object> award = jdbc.queryForMap("select award_id, status, title_uk, title, impact_score, "
            + "organization_id, awarding_organization from awards where source = 'IMPORT' and user_id = ?", employeeId);
        long awardId = ((Number) award.get("award_id")).longValue();
        assertThat(award).containsEntry("status", "APPROVED").containsEntry("title_uk",
            "Почесна грамота обласної ради").containsEntry("title", null).containsEntry("impact_score", 70)
            .containsEntry("organization_id", DDE_DEPARTMENT_ID).containsEntry("awarding_organization", "Обласна рада");
        assertThat(count("select count(*) from award_requests where award_id = ?", awardId)).isZero();
        assertThat(jdbc.queryForObject("select new_values::text from audit_logs where action_type = "
            + "'AWARD_IMPORTED' and entity_id = ?", String.class, awardId))
            .contains("\"file\": \"awards.csv\"", "\"row\": 2");

        JsonPath again = preview(csv, null).then().statusCode(HttpStatus.OK.value()).extract().jsonPath();
        assertThat(again.getList("rows.skipped", Boolean.class)).containsOnly(true);
        assertThat(codes(again, 0)).containsExactly("already-stored");
        run(csv, mapping(again), again.getString("checksum")).then().statusCode(HttpStatus.CREATED.value())
            .body("imported", equalTo(0)).body("peopleAdded", equalTo(0)).body("skipped", equalTo(4));
        assertThat(count(IMPORTED, employeeId)).isEqualTo(1);
    }

    @Test
    void ac5_3_the_owner_sees_the_imported_award_as_approved_and_imported() throws JsonProcessingException {
        String csv = HEADER + "Імпортенко Олена;DDE;Диплом переможця конкурсу;2018;місцевий;Міська рада\n";
        JsonPath preview = preview(csv, null).then().statusCode(HttpStatus.OK.value()).extract().jsonPath();
        run(csv, mapping(preview), preview.getString("checksum")).then().statusCode(HttpStatus.CREATED.value());
        long awardId = jdbc.queryForObject("select award_id from awards where user_id = ? and title_uk = "
            + "'Диплом переможця конкурсу'", Long.class, employeeId);

        as(tokenOf(EMPLOYEE)).get(AwardApi.AWARDS + "/" + awardId).then().statusCode(HttpStatus.OK.value())
            .body("source", equalTo("IMPORT")).body("status", equalTo("APPROVED")).body("request", nullValue())
            .body("visibility", equalTo("PRIVATE"));
    }

    @Test
    void ac5_4_a_file_of_another_type_or_over_the_limits_is_refused() {
        byte[] pdf = "%PDF-1.7\n1 0 obj".getBytes(StandardCharsets.US_ASCII);
        StringBuilder many = new StringBuilder(HEADER);
        for (int row = 0; row <= MAX_ROWS; row++) {
            many.append("Особа Рядок;DDE;Подяка ").append(row).append(";2020;місцевий;\n");
        }

        upload(PREVIEW, "awards.csv", pdf).then().statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body(TYPE, equalTo(PROBLEM + "unsupported-type"));
        preview(many.toString(), null).then().statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body(TYPE, equalTo(PROBLEM + "too-many-rows")).body("maxRows", equalTo(MAX_ROWS));
    }

    @Test
    void ac5_4_rows_outside_the_scope_or_a_changed_file_store_nothing() throws JsonProcessingException {
        String csv = HEADER + "Імпортенко Олена;DDE;Відзнака без шансу;2020;місцевий;Рада\n"
            + "Музиченко Ігор;Кафедра музики;Подяка;2023;університетський;ЧНУ\n";
        JsonPath preview = preview(csv, null).then().statusCode(HttpStatus.OK.value()).extract().jsonPath();
        String mapping = mapping(preview);

        run(csv, mapping, preview.getString("checksum")).then().statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body(TYPE, equalTo(PROBLEM + "rows-blocked")).body("rows", equalTo(List.of(3)));
        run(csv + "Імпортенко Олена;DDE;Ще одна;2020;місцевий;\n", mapping, preview.getString("checksum"))
            .then().statusCode(HttpStatus.CONFLICT.value()).body(TYPE, equalTo(PROBLEM + "preview-stale"));
        assertThat(count("select count(*) from awards where title_uk = 'Відзнака без шансу'")).isZero();
    }

    @Test
    void ac5_4_a_department_outside_the_scope_cannot_be_the_default() {
        String csv = "ПІБ;Назва;Дата\nІмпортенко Олена;Подяка;2020\n";
        String mapping = "{\"columns\":{\"FULL_NAME\":0,\"TITLE\":1,\"DATE\":2},\"departmentId\":"
            + OTHER_FACULTY_ID + ",\"defaultLevel\":\"LOCAL\","
            + "\"defaultAwardingOrganization\":\"Чернівецька міська рада\"}";

        preview(csv, mapping).then().statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body("errors.field", equalTo(List.of("mapping.departmentId")));
        preview(csv, mapping.replace(String.valueOf(OTHER_FACULTY_ID), String.valueOf(DDE_DEPARTMENT_ID)))
            .then().statusCode(HttpStatus.OK.value())
            .body("rows[0].department.id", equalTo((int) DDE_DEPARTMENT_ID))
            .body("rows[0].category.level", equalTo("LOCAL"));
    }

    @Test
    void ac5_4_only_a_faculty_secretary_or_dean_imports() {
        as(tokenOf(EMPLOYEE)).multiPart("file", "awards.csv", HEADER.getBytes(StandardCharsets.UTF_8), "text/csv")
            .post(PREVIEW).then().statusCode(HttpStatus.FORBIDDEN.value());
        as(tokenOf(EMPLOYEE)).get(IMPORTS + "/template").then().statusCode(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void the_template_is_a_workbook_with_the_recognised_columns() {
        Response response = as(tokenOf(SECRETARY)).get(IMPORTS + "/template");

        response.then().statusCode(HttpStatus.OK.value())
            .header("Content-Disposition", containsString("award-import-template.xlsx"))
            .contentType(containsString("spreadsheetml"));
        assertThat(response.asByteArray()).startsWith('P', 'K');
    }

    @Test
    void ac5_4_a_secretary_cannot_import_an_approved_award_of_their_own() {
        String csv = HEADER + "Самоімпорт Ганна;Кафедра диференціальних рівнянь;Грамота;2021;університетський;ЧНУ\n";

        JsonPath preview = as(tokenOf(SELF_SECRETARY))
            .multiPart("file", "awards.csv", csv.getBytes(StandardCharsets.UTF_8), "text/csv")
            .post(PREVIEW).then().statusCode(HttpStatus.OK.value()).extract().jsonPath();

        assertThat(codes(preview, 0)).contains("owner-is-caller");
        assertThat(preview.getBoolean("rows[0].problems.find { it.code == 'owner-is-caller' }.blocking")).isTrue();
    }

    private Response preview(String csv, String mapping) {
        RequestSpecification request = as(tokenOf(SECRETARY))
            .multiPart("file", "awards.csv", csv.getBytes(StandardCharsets.UTF_8), "text/csv");
        if (mapping != null) {
            request.multiPart("mapping", "mapping.json", mapping.getBytes(StandardCharsets.UTF_8),
                "application/json");
        }
        return request.post(PREVIEW);
    }

    private Response run(String csv, String mapping, String checksum) {
        return as(tokenOf(SECRETARY))
            .multiPart("file", "awards.csv", csv.getBytes(StandardCharsets.UTF_8), "text/csv")
            .multiPart("mapping", "mapping.json", mapping.getBytes(StandardCharsets.UTF_8), "application/json")
            .multiPart("checksum", checksum)
            .post(IMPORTS);
    }

    private Response upload(String path, String name, byte[] content) {
        return as(tokenOf(SECRETARY)).multiPart("file", name, content, "text/csv").post(path);
    }

    private String mapping(JsonPath preview) throws JsonProcessingException {
        return json.writeValueAsString(preview.getMap("mapping"));
    }

    private static List<String> codes(JsonPath preview, int row) {
        return preview.getList("rows[" + row + "].problems.code", String.class);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
