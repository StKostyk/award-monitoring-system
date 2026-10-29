package ua.edu.chnu.awards.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import ua.edu.chnu.awards.auth.security.ApiUserRateLimitFilter;
import ua.edu.chnu.awards.auth.security.RateLimitFilter;
import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.ConsentRows;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiRateLimitFT extends AbstractFunctionalTest {

    private static final String PREFIX = "ft.ratelimit.";
    private static final String RECTOR = PREFIX + "rector@chnu.edu.ua";
    private static final String HEAVY = PREFIX + "heavy@chnu.edu.ua";
    private static final String NEIGHBOUR = PREFIX + "neighbour@chnu.edu.ua";
    private static final String PROFILE = "/api/v1/users/me";
    private static final String REPORT = "/api/v1/analytics/university/report";
    private static final String ORIGIN = "http://localhost:4200";
    private static final String TOO_MANY = "urn:awards:problem:too-many-requests";
    private static final String LOOPBACK = "127.0.0.1";
    private static final int MINUTE = 60;
    private static final int SAFE_SECOND = 50;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${app.rate-limit.api-requests-per-minute}")
    private int apiRequestsPerMinute;

    @Value("${app.rate-limit.export-requests-per-minute}")
    private int exportRequestsPerMinute;

    @Value("${app.auth.protection.requests-per-minute}")
    private int authRequestsPerMinute;

    private long rectorId;
    private long heavyId;

    @BeforeAll
    void createAccounts() {
        Organization university = organizationRepository.findById(TestUsers.UNIVERSITY_ID).orElseThrow();
        rectorId = withRole(RECTOR, university, RoleType.RECTOR, university);
        heavyId = activeUser(HEAVY).getId();
        activeUser(NEIGHBOUR);
    }

    @AfterAll
    void deleteAccounts() {
        userRepository.findAll().stream().filter(user -> user.getEmailAddress().startsWith(PREFIX))
            .forEach(user -> {
                ConsentRows.purge(jdbc, user.getId());
                userRepository.delete(user);
            });
    }

    @Test
    void ac2_4_ac2_6_aUserOverTheApiBudgetIsRefusedWhileTheNeighbourBehindTheSameAddressPasses()
            throws InterruptedException {
        String heavy = tokenOf(HEAVY);
        final String neighbour = tokenOf(NEIGHBOUR);
        waitForAFreshWindow();
        fill(ApiUserRateLimitFilter.KEY_PREFIX, heavyId, apiRequestsPerMinute);

        Response refused = as(heavy).header("Origin", ORIGIN).get(PROFILE);

        refused.then().statusCode(HttpStatus.TOO_MANY_REQUESTS.value())
            .header("Retry-After", notNullValue())
            .header("Access-Control-Allow-Origin", ORIGIN)
            .contentType("application/problem+json")
            .body("type", equalTo(TOO_MANY));
        assertThat(Integer.parseInt(refused.getHeader("Retry-After"))).isBetween(1, MINUTE);
        as(neighbour).get(PROFILE).then().statusCode(HttpStatus.OK.value());
        as("not-a-token").get(PROFILE).then().statusCode(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void ac2_6_anAddressOverTheAuthenticationBudgetStillReachesTheSignedInApi() throws InterruptedException {
        final String neighbour = tokenOf(NEIGHBOUR);
        waitForAFreshWindow();
        fill(RateLimitFilter.KEY_PREFIX, LOOPBACK, authRequestsPerMinute);

        RestAssured.given().contentType(ContentType.JSON).body(Map.of("email", NEIGHBOUR))
            .post("/api/v1/auth/password-reset/request").then().statusCode(HttpStatus.TOO_MANY_REQUESTS.value());
        as(neighbour).get(PROFILE).then().statusCode(HttpStatus.OK.value());
    }

    @Test
    void ac2_5_theExportOverTheBudgetIsRefusedBeforeAnyFileOrAuditRow() throws InterruptedException {
        String rector = tokenOf(RECTOR);
        waitForAFreshWindow();
        fill(ApiUserRateLimitFilter.EXPORT_KEY_PREFIX, rectorId, exportRequestsPerMinute);

        as(rector).get(REPORT).then().statusCode(HttpStatus.TOO_MANY_REQUESTS.value())
            .header("Retry-After", notNullValue())
            .body("type", equalTo(TOO_MANY));
        as(rector).get("/api/v1/analytics/university").then().statusCode(HttpStatus.OK.value());
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action_type = 'ANALYTICS_EXPORTED' "
            + "and user_id = ?", Integer.class, rectorId)).isZero();
    }

    private void fill(String keyPrefix, Object subject, int budget) {
        String key = keyPrefix + subject + ":" + Instant.now().getEpochSecond() / MINUTE;
        redis.opsForValue().set(key, String.valueOf(budget), Duration.ofMinutes(2));
    }

    private static void waitForAFreshWindow() throws InterruptedException {
        long second = Instant.now().getEpochSecond() % MINUTE;
        if (second >= SAFE_SECOND) {
            Thread.sleep(Duration.ofSeconds(MINUTE - second + 1).toMillis());
        }
    }
}
