package ua.edu.chnu.awards.gdpr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.RestAssured;

import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.gdpr.service.RetentionJob;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetentionFT extends AbstractFunctionalTest {

    private static final String OFFICER = "ft.retention.officer@chnu.edu.ua";

    @Autowired
    private RetentionJob job;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeAll
    void createOfficer() {
        Organization university = organizationRepository.findById(TestUsers.UNIVERSITY_ID).orElseThrow();
        withRole(OFFICER, university, RoleType.GDPR_OFFICER, university);
    }

    @AfterAll
    void deleteOfficer() {
        jdbc.update("delete from retention_runs");
        userRepository.findByEmailAddressIgnoreCase(OFFICER).ifPresent(userRepository::delete);
    }

    @Test
    void ac1_4_aRunLeftRunningIsReportedToTheOfficerWithALinkToTheCompliancePage() {
        long stale = jdbc.queryForObject("insert into retention_runs (started_at) values (now() - interval '1 day') "
            + "returning run_id", Long.class);
        mailpit.clear();

        assertThat(job.run()).isPresent();

        String subject = "Збереження даних: запуск № " + stale + " не завершено / Data retention: run " + stale
            + " did not complete";
        assertThat(mailpit.latestTextTo(OFFICER, subject))
            .contains("INTERRUPTED", "/compliance", "Hello,", "Вітаємо!")
            .doesNotContain(OFFICER);
    }

    @Test
    void ac1_10_runsAndRowsAreExposedAsMetrics() {
        assertThat(job.run()).isPresent();

        RestAssured.get("/actuator/prometheus").then().statusCode(HttpStatus.OK.value())
            .body(containsString("award_retention_runs_total{"))
            .body(containsString("outcome=\"completed\""))
            .body(containsString("policy=\"devices\""))
            .body(containsString("policy=\"audit-partitions\""));
    }
}
