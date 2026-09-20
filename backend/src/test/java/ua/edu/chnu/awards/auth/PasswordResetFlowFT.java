package ua.edu.chnu.awards.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.util.UriComponentsBuilder;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.AuthorizationCodeFlow;
import ua.edu.chnu.awards.support.Mailpit;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PasswordResetFlowFT extends AbstractIntegrationTest {

    private static final String EMAIL = "ft.reset@chnu.edu.ua";
    private static final String OLD_PASSWORD = "Passw0rd-demo";
    private static final String NEW_PASSWORD = "new-horse-battery";

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Value("${mailpit.api-url}")
    private String mailpitApiUrl;

    private Mailpit mailpit;

    @BeforeAll
    void createUser() {
        User user = TestUsers.user(EMAIL, organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow());
        userRepository.save(user);
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        mailpit = new Mailpit(mailpitApiUrl);
    }

    @AfterAll
    void cleanUp() {
        userRepository.findByEmailAddressIgnoreCase(EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    void ac31_ac32_ac33_requestResetSignInWithTheNewPasswordAndLoseOldSessions() {
        mailpit.clear();
        AuthorizationCodeFlow before = new AuthorizationCodeFlow();
        String refreshToken = before.exchange(before.loginAndGetCode(EMAIL, OLD_PASSWORD))
            .jsonPath().getString("refresh_token");

        requestReset(EMAIL).then().statusCode(202);
        requestReset(EMAIL).then().statusCode(202);
        requestReset("nobody@chnu.edu.ua").then().statusCode(202);
        String text = mailpit.latestTextTo(EMAIL);
        assertThat(mailpit.messagesTo(EMAIL)).hasSize(1);
        assertThat(mailpit.messagesTo("nobody@chnu.edu.ua")).isEmpty();
        String link = Mailpit.linkIn(text);
        assertThat(link).startsWith("http://localhost:4200/reset-password?token=");
        String token = UriComponentsBuilder.fromUri(URI.create(link)).build().getQueryParams().getFirst("token");

        confirmReset(token, "password123").then().statusCode(422)
            .body("type", equalTo("urn:awards:problem:password-too-common"));
        confirmReset(token, NEW_PASSWORD).then().statusCode(204);
        confirmReset(token, NEW_PASSWORD).then().statusCode(410)
            .body("type", equalTo("urn:awards:problem:token-invalid"));

        AuthorizationCodeFlow.refresh(refreshToken).then().statusCode(400).body("error", equalTo("invalid_grant"));
        assertThat(before.authorize().getHeader("Location")).doesNotStartWith(AuthorizationCodeFlow.REDIRECT_URI);
        assertThat(before.authorize().getHeader("Location")).endsWith("/login");

        AuthorizationCodeFlow oldPassword = new AuthorizationCodeFlow();
        oldPassword.authorize();
        assertThat(oldPassword.submitLogin(EMAIL, OLD_PASSWORD).getHeader("Location"))
            .endsWith("?error=BAD_CREDENTIALS");

        AuthorizationCodeFlow newPassword = new AuthorizationCodeFlow();
        newPassword.exchange(newPassword.loginAndGetCode(EMAIL, NEW_PASSWORD)).then().statusCode(200)
            .body("access_token", notNullValue());
    }

    private static Response requestReset(String email) {
        return RestAssured.given().contentType(ContentType.JSON).body(Map.of("email", email))
            .post("/api/v1/auth/password-reset/request");
    }

    private static Response confirmReset(String token, String password) {
        return RestAssured.given().contentType(ContentType.JSON).body(Map.of("token", token, "password", password))
            .post("/api/v1/auth/password-reset/confirm");
    }
}
