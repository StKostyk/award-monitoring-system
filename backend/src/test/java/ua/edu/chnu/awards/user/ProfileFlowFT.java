package ua.edu.chnu.awards.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.net.URI;
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
import org.springframework.web.util.UriComponentsBuilder;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.AuthorizationCodeFlow;
import ua.edu.chnu.awards.support.Mailpit;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProfileFlowFT extends AbstractFunctionalTest {

    private static final String RENAMER = "ft.renamer@chnu.edu.ua";
    private static final String MOVER = "ft.mover@chnu.edu.ua";
    private static final String MOVED = "ft.mover.new@chnu.edu.ua";
    private static final String GUESSER = "ft.guesser@chnu.edu.ua";
    private static final int MAX_FAILURES = 5;

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @Value("${mailpit.api-url}")
    private String mailpitApiUrl;

    private Mailpit mailpit;

    @BeforeAll
    void createUsers() {
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        List.of(RENAMER, MOVER, GUESSER).forEach(email -> userRepository.save(TestUsers.user(email, department)));
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        mailpit = new Mailpit(mailpitApiUrl);
    }

    @AfterAll
    void deleteUsers() {
        List.of(RENAMER, MOVER, MOVED, GUESSER).forEach(email -> userRepository.findByEmailAddressIgnoreCase(email)
            .ifPresent(userRepository::delete));
        redis.delete(redis.keys("auth:lock:ft.guesser*"));
    }

    @Test
    void ac11_ac12_ac13_theOwnerCorrectsTheNameThroughTheApi() {
        String token = tokenOf(RENAMER);
        as(token).get("/api/v1/users/me").then().statusCode(200)
            .body("organization.id", equalTo(64)).body("faculty.type", equalTo("FACULTY"));

        rename(token, Map.of("lastName", "Петренко-Коваль")).then().statusCode(200)
            .body("lastName", equalTo("Петренко-Коваль")).body("firstName", equalTo("Test"));
        rename(token, Map.of("lastName", "X1")).then().statusCode(422)
            .body("type", equalTo("urn:awards:problem:validation-failed"))
            .body("errors[0].field", equalTo("lastName")).body("errors[0].code", equalTo("invalid"));
        rename(token, Map.of("email", "x@chnu.edu.ua")).then().statusCode(422)
            .body("errors[0].field", equalTo("email")).body("errors[0].code", equalTo("not-allowed"));
        rename(token, Map.of("lastName", "Петренко-Коваль")).then().statusCode(200);

        long userId = Long.parseLong(String.valueOf(claims(token).get("sub")));
        List<Map<String, Object>> audit = jdbc.queryForList("""
            select host(ip_address) as ip, correlation_id, array_to_string(changed_fields, ',') as changed
            from audit_logs where user_id = ? and action_type = 'PROFILE_UPDATED'
            """, userId);
        assertThat(audit).hasSize(1);
        assertThat(audit.get(0)).containsEntry("changed", "last_name");
        assertThat(audit.get(0).get("ip")).isNotNull();
        assertThat(audit.get(0).get("correlation_id")).isNotNull();
    }

    @Test
    void ac14_ac15_ac16_theAccountMovesToAConfirmedAddressAndOldSessionsEnd() {
        mailpit.clear();
        AuthorizationCodeFlow before = new AuthorizationCodeFlow();
        Response tokens = before.exchange(before.loginAndGetCode(MOVER, DEMO_PASSWORD));
        String accessToken = tokens.jsonPath().getString("access_token");
        String refreshToken = tokens.jsonPath().getString("refresh_token");

        requestChange(accessToken, MOVED, "wrong-password").then().statusCode(403)
            .body("type", equalTo("urn:awards:problem:password-mismatch"));
        requestChange(accessToken, "ft.mover@gmail.com", DEMO_PASSWORD).then().statusCode(422)
            .body("type", equalTo("urn:awards:problem:institutional-email-required"));
        requestChange(accessToken, RENAMER, DEMO_PASSWORD).then().statusCode(409)
            .body("type", equalTo("urn:awards:problem:email-taken"));
        requestChange(accessToken, MOVED, DEMO_PASSWORD).then().statusCode(202);
        requestChange(accessToken, MOVED, DEMO_PASSWORD).then().statusCode(429);

        String link = Mailpit.linkIn(mailpit.latestTextTo(MOVED, "Confirm your new address"));
        assertThat(link).startsWith("http://localhost:4200/confirm-email-change?token=");
        String token = UriComponentsBuilder.fromUri(URI.create(link)).build().getQueryParams().getFirst("token");

        confirm(token).then().statusCode(200).body("email", equalTo(MOVED));
        confirm(token).then().statusCode(410).body("type", equalTo("urn:awards:problem:token-invalid"));
        assertThat(mailpit.latestTextTo(MOVER, "sign-in address was changed")).contains(MOVED);

        as(accessToken).get("/api/v1/users/me").then().statusCode(401);
        AuthorizationCodeFlow.refresh(refreshToken).then().statusCode(400).body("error", equalTo("invalid_grant"));
        AuthorizationCodeFlow oldAddress = new AuthorizationCodeFlow();
        oldAddress.authorize();
        assertThat(oldAddress.submitLogin(MOVER, DEMO_PASSWORD).getHeader("Location"))
            .endsWith("?error=BAD_CREDENTIALS");
        as(tokenOf(MOVED)).get("/api/v1/users/me").then().statusCode(200).body("email", equalTo(MOVED));
    }

    @Test
    void edge_fiveWrongPasswordsLockTheAccountAndEndItsSessions() {
        String accessToken = tokenOf(GUESSER);

        for (int attempt = 1; attempt <= MAX_FAILURES; attempt++) {
            requestChange(accessToken, "ft.guesser.new@chnu.edu.ua", "wrong-" + attempt).then().statusCode(403);
        }

        as(accessToken).get("/api/v1/users/me").then().statusCode(401);
        AuthorizationCodeFlow locked = new AuthorizationCodeFlow();
        locked.authorize();
        assertThat(locked.submitLogin(GUESSER, DEMO_PASSWORD).getHeader("Location")).endsWith("?error=LOCKED");
    }

    private static Response rename(String token, Map<String, Object> body) {
        return as(token).contentType(ContentType.JSON).body(body).patch("/api/v1/users/me");
    }

    private static Response requestChange(String token, String newEmail, String password) {
        return as(token).contentType(ContentType.JSON)
            .body(Map.of("newEmail", newEmail, "currentPassword", password))
            .post("/api/v1/users/me/email-change");
    }

    private static Response confirm(String token) {
        return RestAssured.given().contentType(ContentType.JSON).body(Map.of("token", token))
            .post("/api/v1/auth/email-change/confirm");
    }
}
