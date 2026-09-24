package ua.edu.chnu.awards.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.AuthorizationCodeFlow;
import ua.edu.chnu.awards.support.Mailpit;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
class RegistrationFlowFT extends AbstractIntegrationTest {

    private static final String EMAIL = "ft.register@chnu.edu.ua";
    private static final String ABANDONED = "ft.abandoned@chnu.edu.ua";
    private static final String PASSWORD = "correct-horse-battery";

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${mailpit.api-url}")
    private String mailpitApiUrl;

    private Mailpit mailpit;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        mailpit = new Mailpit(mailpitApiUrl);
    }

    @AfterAll
    void cleanUp() {
        List.of(EMAIL, ABANDONED).forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @Test
    void ac21_ac25_registerVerifyAndSignIn() {
        mailpit.clear();

        register(EMAIL, 64L).then().statusCode(201)
            .body("email", equalTo(EMAIL))
            .body("status", equalTo("PENDING"));

        AuthorizationCodeFlow beforeVerification = new AuthorizationCodeFlow();
        beforeVerification.authorize();
        assertThat(beforeVerification.submitLogin(EMAIL, PASSWORD).getHeader("Location")).endsWith("?error=PENDING");

        String link = Mailpit.linkIn(mailpit.latestTextTo(EMAIL));
        assertThat(link).startsWith("http://localhost:4200/verify-email?token=");
        String token = UriComponentsBuilder.fromUri(URI.create(link)).build().getQueryParams().getFirst("token");

        verify(token, "not-the-registration-password").then().statusCode(403)
            .body("type", equalTo("urn:awards:problem:password-mismatch"));
        verify(token, PASSWORD).then().statusCode(200).body("status", equalTo("ACTIVE"));
        verify(token, PASSWORD).then().statusCode(410).body("type", equalTo("urn:awards:problem:token-invalid"));

        AuthorizationCodeFlow flow = new AuthorizationCodeFlow();
        Response tokens = flow.exchange(flow.loginAndGetCode(EMAIL, PASSWORD));
        tokens.then().statusCode(200).body("access_token", notNullValue());
        RestAssured.given().header("Authorization", "Bearer " + tokens.jsonPath().getString("access_token"))
            .get("/api/v1/users/me").then().statusCode(200)
            .body("status", equalTo("ACTIVE"))
            .body("roles", equalTo(List.of()))
            .body("membershipConfirmed", equalTo(false))
            .body("organization.code", equalTo("DAI"));
    }

    @Test
    void ac22_ac23_ac24_registrationRefusals() {
        register("someone@gmail.com", 64L).then().statusCode(422)
            .body("type", equalTo("urn:awards:problem:institutional-email-required"));
        register("ft.faculty@chnu.edu.ua", 9L).then().statusCode(422)
            .body("type", equalTo("urn:awards:problem:organisation-invalid"));
        register("FT.REGISTER@chnu.edu.ua", 64L).then().statusCode(409)
            .body("type", equalTo("urn:awards:problem:email-taken"));
        assertThat(userRepository.existsByEmailAddressIgnoreCase("someone@gmail.com")).isFalse();
    }

    @Test
    void ac2_8_anAbandonedPendingAccountIsTakenOverByTheNextRegistration() {
        mailpit.clear();
        register(ABANDONED, 64L).then().statusCode(201);
        register(ABANDONED, 64L).then().statusCode(409)
            .body("type", equalTo("urn:awards:problem:email-taken"));

        jdbc.update("update one_time_tokens set expires_at = now() - interval '1 hour'"
            + " where purpose = 'EMAIL_VERIFICATION' and user_id ="
            + " (select user_id from users where email_address = ?)", ABANDONED);

        RestAssured.given().contentType(ContentType.JSON)
            .body(Map.of("email", ABANDONED, "password", PASSWORD, "firstName", "Друга", "lastName", "Спроба",
                "organizationId", 65L))
            .post("/api/v1/auth/register").then().statusCode(201).body("status", equalTo("PENDING"));

        assertThat(userRepository.findByEmailAddressIgnoreCase(ABANDONED).orElseThrow().getFirstName())
            .isEqualTo("Друга");
        assertThat(Mailpit.linkIn(mailpit.latestTextTo(ABANDONED, "Підтвердження адреси", 2)))
            .startsWith("http://localhost:4200/verify-email?token=");
    }

    @Test
    void ac26_resendIsThrottledAndSilentAboutUnknownAddresses() {
        String address = "ft.resend@chnu.edu.ua";
        resend(address).then().statusCode(202);
        resend(address).then().statusCode(429)
            .body("type", equalTo("urn:awards:problem:too-many-requests"));
    }

    @Test
    void ac27_departmentsAreListedWithTheirFacultyWithoutAToken() {
        RestAssured.when().get("/api/v1/organizations?type=DEPARTMENT").then().statusCode(200)
            .body("size()", greaterThan(50))
            .body("find { it.code == 'DAI' }.nameUk", equalTo("Кафедра алгебри та інформатики"))
            .body("find { it.code == 'DAI' }.parent.code", equalTo("FMI"))
            .body("find { it.code == 'DAI' }.parent.type", equalTo("FACULTY"));
        RestAssured.when().get("/api/v1/organizations?type=FACULTY").then().statusCode(200)
            .body("find { it.code == 'FMI' }.parent.type", equalTo("UNIVERSITY"));
    }

    private static Response register(String email, long organizationId) {
        return RestAssured.given().contentType(ContentType.JSON)
            .body(Map.of("email", email, "password", PASSWORD, "firstName", "Олена", "lastName", "Нова",
                "organizationId", organizationId))
            .post("/api/v1/auth/register");
    }

    private static Response verify(String token, String password) {
        return RestAssured.given().contentType(ContentType.JSON).body(Map.of("token", token, "password", password))
            .post("/api/v1/auth/verify-email");
    }

    private static Response resend(String email) {
        return RestAssured.given().contentType(ContentType.JSON).body(Map.of("email", email))
            .post("/api/v1/auth/resend-verification");
    }
}
