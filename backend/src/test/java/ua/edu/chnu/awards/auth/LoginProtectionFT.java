package ua.edu.chnu.awards.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.AuthorizationCodeFlow;
import ua.edu.chnu.awards.support.Mailpit;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoginProtectionFT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Passw0rd-demo";
    private static final String TARGET = "ft.locked@chnu.edu.ua";
    private static final String ADMIN = "ft.admin@chnu.edu.ua";
    private static final String BURST_IP = "10.99.0.1";
    private static final int MAX_FAILURES = 5;
    private static final int MINUTE = 60;
    private static final int SAFE_SECOND = 40;

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${mailpit.api-url}")
    private String mailpitApiUrl;

    @Value("${app.auth.protection.requests-per-minute}")
    private int requestsPerMinute;

    private Mailpit mailpit;
    private Long targetId;

    @BeforeAll
    void createUsers() {
        Organization university = organizationRepository.findById(TestUsers.UNIVERSITY_ID).orElseThrow();
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        targetId = userRepository.save(TestUsers.user(TARGET, department)).getId();
        User admin = userRepository.save(TestUsers.user(ADMIN, university));
        userRoleRepository.save(TestUsers.role(admin, RoleType.SYSTEM_ADMIN, university,
            LocalDate.now().minusDays(1), null));
    }

    @AfterAll
    void deleteUsers() {
        redis.delete(List.of("auth:lock:" + TARGET, "auth:fail:" + TARGET));
        List.of(TARGET, ADMIN).forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        mailpit = new Mailpit(mailpitApiUrl);
    }

    @Test
    void ac41_ac43_ac44_ac45_fiveFailuresLockTheAccountNotifyAdminsAndAreAudited() {
        mailpit.clear();

        for (int attempt = 1; attempt < MAX_FAILURES; attempt++) {
            assertThat(login(TARGET, "wrong-" + attempt)).endsWith("/login?error=BAD_CREDENTIALS");
        }
        assertThat(login(TARGET, "wrong-5")).endsWith("/login?error=LOCKED");
        assertThat(login(TARGET, PASSWORD)).endsWith("/login?error=LOCKED");
        for (int attempt = 1; attempt <= MAX_FAILURES + 1; attempt++) {
            assertThat(login("ghost@chnu.edu.ua", "wrong-" + attempt))
                .endsWith(attempt < MAX_FAILURES ? "/login?error=BAD_CREDENTIALS" : "/login?error=LOCKED");
        }
        redis.delete("auth:lock:ghost@chnu.edu.ua");

        Long ttl = redis.getExpire("auth:lock:" + TARGET);
        assertThat(ttl).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(30).toSeconds());

        String mail = mailpit.latestTextTo(ADMIN);
        assertThat(mail).contains(TARGET).contains("127.0.0.1").contains("30 minutes");
        assertThat(mailpit.messagesTo(ADMIN)).hasSize(1);

        List<Map<String, Object>> rows = jdbc.queryForList(
            "select action_type, host(ip_address) as ip, user_agent, correlation_id from audit_logs "
                + "where user_id = ? and entity_type = 'AUTHENTICATION' order by created_at, log_id", targetId);
        assertThat(rows).extracting(row -> row.get("action_type"))
            .containsExactly("LOGIN_FAILED", "LOGIN_FAILED", "LOGIN_FAILED", "LOGIN_FAILED", "ACCOUNT_LOCKED",
                "LOGIN_FAILED", "LOGIN_FAILED");
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get("ip")).isEqualTo("127.0.0.1");
            assertThat(row.get("user_agent")).isNotNull();
            assertThat(row.get("correlation_id")).isNotNull();
        });

        redis.delete("auth:lock:" + TARGET);
        AuthorizationCodeFlow flow = new AuthorizationCodeFlow();
        flow.exchange(flow.loginAndGetCode(TARGET, PASSWORD)).then().statusCode(200)
            .body("access_token", notNullValue());
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where user_id = ? and action_type = ?",
            Integer.class, targetId, "LOGIN_SUCCESS")).isEqualTo(1);
    }

    @Test
    void ac42_aBurstFromOneAddressIsRefusedWithRetryAfter() throws InterruptedException {
        waitForAFreshWindow();

        for (int i = 0; i < requestsPerMinute; i++) {
            resetRequest().then().statusCode(202);
        }
        Response refused = resetRequest();
        refused.then().statusCode(429)
            .header("Retry-After", notNullValue())
            .contentType("application/problem+json")
            .body("type", equalTo("urn:awards:problem:too-many-requests"));
        assertThat(Integer.parseInt(refused.getHeader("Retry-After"))).isBetween(1, MINUTE);

        Response page = RestAssured.given().header("X-Forwarded-For", BURST_IP).accept("text/html").get("/login");
        page.then().statusCode(429).header("Retry-After", notNullValue());
        assertThat(page.asString()).contains("Забагато запитів");

        RestAssured.given().header("X-Forwarded-For", "10.99.0.2").accept("text/html").get("/login")
            .then().statusCode(200);
        RestAssured.given().header("X-Real-IP", BURST_IP).accept("text/html").get("/login")
            .then().statusCode(200);
    }

    @Test
    void ac43_everyResponseCarriesACorrelationId() {
        RestAssured.given().accept("text/html").get("/login")
            .then().statusCode(200).header("X-Correlation-Id", notNullValue());
    }

    private static String login(String email, String password) {
        AuthorizationCodeFlow flow = new AuthorizationCodeFlow();
        flow.authorize();
        return flow.submitLogin(email, password).getHeader("Location");
    }

    private static Response resetRequest() {
        return RestAssured.given().header("X-Forwarded-For", BURST_IP).contentType(ContentType.JSON)
            .body(Map.of("email", "nobody@chnu.edu.ua"))
            .post("/api/v1/auth/password-reset/request");
    }

    private static void waitForAFreshWindow() throws InterruptedException {
        long second = Instant.now().getEpochSecond() % MINUTE;
        if (second >= SAFE_SECOND) {
            Thread.sleep(Duration.ofSeconds(MINUTE - second + 1).toMillis());
        }
    }
}
