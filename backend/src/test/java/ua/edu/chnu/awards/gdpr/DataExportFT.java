package ua.edu.chnu.awards.gdpr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.response.Response;

import ua.edu.chnu.awards.gdpr.service.DataExportService;
import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.AwardRows;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataExportFT extends AbstractFunctionalTest {

    private static final String EXPORTER = "ft.exporter@chnu.edu.ua";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    private User exporter;

    @BeforeAll
    void createUser() {
        exporter = activeUser(EXPORTER);
        AwardRows.award(jdbc, exporter.getId()).title("Draft letter").insert();
    }

    @AfterAll
    void deleteUser() {
        jdbc.update("delete from awards where user_id = ?", exporter.getId());
        userRepository.deleteById(exporter.getId());
        redis.delete(DataExportService.THROTTLE_KEY_PREFIX + exporter.getId());
    }

    @Test
    void ac31_ac33_ac34_theOwnerDownloadsTheDataOnceAMinuteAndIsTold() {
        mailpit.clear();
        String token = tokenOf(EXPORTER);

        Response export = as(token).header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101"
            + " Firefox/130.0").get("/api/v1/users/me/export");

        export.then().statusCode(200)
            .contentType(containsString("application/json"))
            .header("Content-Disposition", containsString("attachment; filename=\"award-monitoring-export-"))
            .header("Cache-Control", containsString("no-store"))
            .body("export_metadata.user_id", equalTo(exporter.getId().intValue()))
            .body("personal_data.profile.email", equalTo(EXPORTER))
            .body("awards", hasSize(1))
            .body("awards[0].status", equalTo("DRAFT"))
            .body("roles", hasSize(0))
            .body("activity_log.action", hasItem("LOGIN_SUCCESS"));
        assertThat(export.asString()).doesNotContain("password", "$2a$", "token");

        String notice = mailpit.latestTextTo(EXPORTER, "Your data was exported");
        assertThat(notice).contains("Firefox", "IP: ");

        as(token).get("/api/v1/users/me/export").then().statusCode(429)
            .header("Retry-After", "60")
            .body("type", equalTo("urn:awards:problem:too-many-requests"));
    }
}
