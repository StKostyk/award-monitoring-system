package ua.edu.chnu.awards.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

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

import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.AuthorizationCodeFlow;
import ua.edu.chnu.awards.support.Mailpit;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeviceNotificationFT extends AbstractIntegrationTest {

    private static final String EMAIL = "ft.device@chnu.edu.ua";
    private static final String PASSWORD = "Passw0rd-demo";
    private static final String NEW_PASSWORD = "battery-horse-staple";
    private static final String NEW_SIGN_IN = "New sign-in";
    private static final String CHROME_WINDOWS = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final String FIREFOX_MAC = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14.6; rv:130.0) "
        + "Gecko/20100101 Firefox/130.0";

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
    private Long userId;

    @BeforeAll
    void createUser() {
        User user = TestUsers.user(EMAIL, organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow());
        userId = userRepository.save(user).getId();
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        mailpit = new Mailpit(mailpitApiUrl);
    }

    @AfterAll
    void cleanUp() {
        redis.delete(List.of("auth:lock:" + EMAIL, "auth:fail:" + EMAIL));
        userRepository.findByEmailAddressIgnoreCase(EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    void ac51_ac52_ac53_newBrowsersAreAnnouncedOnceAndTheNotMeLinkEndsEverySession() {
        mailpit.clear();

        final String chromeRefresh = signIn(CHROME_WINDOWS, "uk-UA,uk;q=0.9");
        String chromeMail = mailpit.latestTextTo(EMAIL, NEW_SIGN_IN);
        assertThat(chromeMail).contains("Chrome").contains("Windows").contains("127.0.0.1").contains("Test");
        String chromeToken = tokenOf(Mailpit.linkIn(chromeMail));
        assertThat(chromeToken).isNotBlank();
        assertKnownBrowserIsSilent();

        final String firefoxRefresh = signIn(FIREFOX_MAC, "en-US,en;q=0.9");
        String firefoxMail = mailpit.latestTextTo(EMAIL, NEW_SIGN_IN, 2);
        assertThat(firefoxMail).contains("Firefox").contains("Mac OS X");
        assertThat(mailpit.messagesTo(EMAIL, NEW_SIGN_IN)).hasSize(2);
        assertThat(devices()).hasSize(2);
        String token = tokenOf(Mailpit.linkIn(firefoxMail));

        revoke(token).then().statusCode(204);
        revoke(token).then().statusCode(410).body("type", equalTo("urn:awards:problem:token-invalid"));
        revoke(chromeToken).then().statusCode(410);

        AuthorizationCodeFlow.refresh(chromeRefresh).then().statusCode(400).body("error", equalTo("invalid_grant"));
        AuthorizationCodeFlow.refresh(firefoxRefresh).then().statusCode(400).body("error", equalTo("invalid_grant"));
        assertAccountRevokedButActive();
        recoverWithTheResetLink();
    }

    private void assertKnownBrowserIsSilent() {
        List<Map<String, Object>> devices = devices();
        assertThat(devices).hasSize(1);
        assertThat(devices.get(0)).containsEntry("browser", "Chrome").containsEntry("operating_system", "Windows")
            .containsEntry("last_ip_address", "127.0.0.1");
        jdbc.update("update user_devices set last_used_at = now() - interval '1 day', last_ip_address = '10.0.0.1' "
            + "where user_id = ?", userId);
        final double staleUse = ((Number) devices().get(0).get("last_used")).doubleValue();

        signIn(CHROME_WINDOWS, "uk-UA,uk;q=0.9");

        List<Map<String, Object>> refreshed = devices();
        assertThat(refreshed).hasSize(1);
        assertThat(refreshed.get(0)).containsEntry("last_ip_address", "127.0.0.1");
        assertThat(((Number) refreshed.get(0).get("last_used")).doubleValue()).isGreaterThan(staleUse + 3600);
        assertThat(revokeTokens()).isEqualTo(1);
    }

    private void assertAccountRevokedButActive() {
        assertThat(devices()).isEmpty();
        assertThat(userRepository.findById(userId).orElseThrow().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where user_id = ? and action_type = ?",
            Integer.class, userId, "SECURITY_REVOKE")).isEqualTo(1);
        AuthorizationCodeFlow oldPassword = new AuthorizationCodeFlow();
        oldPassword.authorize();
        assertThat(oldPassword.submitLogin(EMAIL, PASSWORD).getHeader("Location")).endsWith("?error=BAD_CREDENTIALS");
    }

    private void recoverWithTheResetLink() {
        String resetToken = tokenOf(Mailpit.linkIn(mailpit.latestTextTo(EMAIL, "Password reset")));
        RestAssured.given().contentType(ContentType.JSON).body(Map.of("token", resetToken, "password", NEW_PASSWORD))
            .post("/api/v1/auth/password-reset/confirm").then().statusCode(204);
        AuthorizationCodeFlow newPassword = new AuthorizationCodeFlow().header("User-Agent", CHROME_WINDOWS);
        newPassword.exchange(newPassword.loginAndGetCode(EMAIL, NEW_PASSWORD)).then().statusCode(200)
            .body("access_token", notNullValue());
        assertThat(devices()).hasSize(1);
    }

    private static String signIn(String userAgent, String acceptLanguage) {
        AuthorizationCodeFlow flow = new AuthorizationCodeFlow()
            .header("User-Agent", userAgent)
            .header("Accept-Language", acceptLanguage);
        Response tokens = flow.exchange(flow.loginAndGetCode(EMAIL, PASSWORD));
        tokens.then().statusCode(200);
        return tokens.jsonPath().getString("refresh_token");
    }

    private static Response revoke(String token) {
        return RestAssured.given().contentType(ContentType.JSON).body(Map.of("token", token))
            .post("/api/v1/auth/security/revoke");
    }

    private static String tokenOf(String link) {
        return UriComponentsBuilder.fromUri(URI.create(link)).build().getQueryParams().getFirst("token");
    }

    private List<Map<String, Object>> devices() {
        return jdbc.queryForList("select browser, operating_system, last_ip_address, "
            + "extract(epoch from last_used_at) as last_used from user_devices where user_id = ? "
            + "order by first_seen_at", userId);
    }

    private int revokeTokens() {
        Integer count = jdbc.queryForObject(
            "select count(*) from one_time_tokens where user_id = ? and purpose = 'SECURITY_REVOKE'", Integer.class,
            userId);
        return count == null ? 0 : count;
    }
}
