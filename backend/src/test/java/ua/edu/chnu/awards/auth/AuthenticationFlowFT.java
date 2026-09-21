package ua.edu.chnu.awards.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import io.restassured.RestAssured;
import io.restassured.response.Response;

import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.AuthorizationCodeFlow;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.AccountStatus;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthenticationFlowFT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Passw0rd-demo";
    private static final String DEAN = "ft.dean@chnu.edu.ua";
    private static final String PENDING = "ft.pending@chnu.edu.ua";
    private static final String SUSPENDED = "ft.suspended@chnu.edu.ua";

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @BeforeAll
    void createUsers() {
        Organization faculty = organizationRepository.findById(TestUsers.FMI_FACULTY_ID).orElseThrow();
        User dean = userRepository.save(TestUsers.user(DEAN, faculty));
        userRoleRepository.save(TestUsers.role(dean, RoleType.DEAN, faculty, LocalDate.now().minusDays(1), null));
        userRoleRepository.save(TestUsers.role(dean, RoleType.EMPLOYEE, faculty, LocalDate.now().minusDays(1), null));
        User pending = TestUsers.user(PENDING, faculty);
        pending.setAccountStatus(AccountStatus.PENDING);
        userRepository.save(pending);
        User suspended = TestUsers.user(SUSPENDED, faculty);
        suspended.setAccountStatus(AccountStatus.SUSPENDED);
        userRepository.save(suspended);
    }

    @AfterAll
    void deleteUsers() {
        List.of(DEAN, PENDING, SUSPENDED).forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void ac11_anonymousAuthorizeRequestIsSentToTheLoginPage() {
        Response response = new AuthorizationCodeFlow().authorize();

        assertThat(response.getStatusCode()).isEqualTo(302);
        assertThat(response.getHeader("Location")).endsWith("/login");
    }

    @Test
    void ac12_ac14_loginIssuesPkceCodeExchangeableForSignedTokens() {
        AuthorizationCodeFlow flow = new AuthorizationCodeFlow();
        String code = flow.loginAndGetCode(DEAN, PASSWORD);

        Response tokens = flow.exchange(code);

        tokens.then().statusCode(200)
            .body("token_type", equalTo("Bearer"))
            .body("refresh_token", notNullValue())
            .body("id_token", notNullValue());

        assertThat(tokens.jsonPath().getInt("expires_in")).isBetween(880, 900);
        String accessToken = tokens.jsonPath().getString("access_token");
        JwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri("http://localhost:" + port + "/oauth2/jwks").build();
        Jwt jwt = decoder.decode(accessToken);
        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256").containsKey("kid");
        assertThat(jwt.getClaimAsString("email")).isEqualTo(DEAN);
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("EMPLOYEE", "DEAN");
        assertThat(jwt.getClaimAsStringList("permissions")).contains("award:approve:level2");
        assertThat(jwt.getClaims()).containsEntry("org_type", "FACULTY");
        assertThat(jwt.getClaimAsString("sub")).isEqualTo(String.valueOf(
            userRepository.findByEmailAddressIgnoreCase(DEAN).orElseThrow().getId()));

        RestAssured.given().header("Authorization", "Bearer " + accessToken)
            .get("/api/v1/users/me")
            .then().statusCode(200)
            .body("email", equalTo(DEAN))
            .body("roles.role", hasItem("DEAN"))
            .body("organization.code", equalTo("FMI"))
            .body("lastLoginAt", notNullValue());
    }

    @Test
    void ac12_codeExchangeRequiresTheMatchingVerifier() {
        AuthorizationCodeFlow flow = new AuthorizationCodeFlow();
        String code = flow.loginAndGetCode(DEAN, PASSWORD);

        flow.exchange(code, "not-the-verifier-that-was-used-to-start-the-flow").then().statusCode(400)
            .body("error", equalTo("invalid_grant"));
        RestAssured.given()
            .formParam("grant_type", "authorization_code")
            .formParam("code", code)
            .formParam("redirect_uri", AuthorizationCodeFlow.REDIRECT_URI)
            .formParam("client_id", AuthorizationCodeFlow.CLIENT_ID)
            .formParam("token", "stray")
            .post("/oauth2/token").then().statusCode(401);
    }

    @Test
    void ac15_refreshRotatesAndReuseRevokesTheWholeAuthorization() {
        AuthorizationCodeFlow flow = new AuthorizationCodeFlow();
        Response first = flow.exchange(flow.loginAndGetCode(DEAN, PASSWORD));
        String firstRefresh = first.jsonPath().getString("refresh_token");

        Response second = AuthorizationCodeFlow.refresh(firstRefresh);
        second.then().statusCode(200).body("refresh_token", notNullValue());
        String secondRefresh = second.jsonPath().getString("refresh_token");
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);

        AuthorizationCodeFlow.refresh(firstRefresh).then().statusCode(400).body("error", equalTo("invalid_grant"));
        AuthorizationCodeFlow.refresh(secondRefresh).then().statusCode(400).body("error", equalTo("invalid_grant"));
    }

    @Test
    void ac15_suspendedAccountCannotRefreshAndIdTokenIsNotABearerToken() {
        AuthorizationCodeFlow flow = new AuthorizationCodeFlow();
        Response tokens = flow.exchange(flow.loginAndGetCode(DEAN, PASSWORD));
        String refreshToken = tokens.jsonPath().getString("refresh_token");

        RestAssured.given().header("Authorization", "Bearer " + tokens.jsonPath().getString("id_token"))
            .get("/api/v1/users/me").then().statusCode(401);

        setStatus(DEAN, AccountStatus.SUSPENDED);
        try {
            AuthorizationCodeFlow.refresh(refreshToken).then().statusCode(400).body("error", equalTo("invalid_grant"));
        } finally {
            setStatus(DEAN, AccountStatus.ACTIVE);
        }
        AuthorizationCodeFlow.refresh(refreshToken).then().statusCode(400).body("error", equalTo("invalid_grant"));
    }

    private void setStatus(String email, AccountStatus status) {
        User user = userRepository.findByEmailAddressIgnoreCase(email).orElseThrow();
        user.setAccountStatus(status);
        userRepository.saveAndFlush(user);
    }

    @Test
    void ac16_pendingAndSuspendedUsersAreRefusedWithTheirStatus() {
        AuthorizationCodeFlow pending = new AuthorizationCodeFlow();
        pending.authorize();
        assertThat(pending.submitLogin(PENDING, PASSWORD).getHeader("Location")).endsWith("/login?error=PENDING");

        AuthorizationCodeFlow suspended = new AuthorizationCodeFlow();
        suspended.authorize();
        assertThat(suspended.submitLogin(SUSPENDED, PASSWORD).getHeader("Location"))
            .endsWith("/login?error=SUSPENDED");

        AuthorizationCodeFlow wrong = new AuthorizationCodeFlow();
        wrong.authorize();
        assertThat(wrong.submitLogin(DEAN, "wrong").getHeader("Location")).endsWith("/login?error=BAD_CREDENTIALS");

        AuthorizationCodeFlow probe = new AuthorizationCodeFlow();
        probe.authorize();
        assertThat(probe.submitLogin(SUSPENDED, "wrong").getHeader("Location"))
            .endsWith("/login?error=BAD_CREDENTIALS");
        assertThat(probe.loginPage("error=DROP%20TABLE").asString()).contains("lang=").doesNotContain("DROP");
    }

    @Test
    void ac17_loginPageIsUkrainianByDefaultAndEnglishOnRequest() {
        AuthorizationCodeFlow flow = new AuthorizationCodeFlow();

        String ukrainian = flow.loginPage("").asString();
        assertThat(ukrainian).contains("lang=\"uk\"").contains("Увійти");

        String english = flow.loginPage("lang=en").asString();
        assertThat(english).contains("lang=\"en\"").contains("Sign in");
        assertThat(flow.cookies()).containsEntry("lang", "en");

        assertThat(flow.loginPage("").asString()).contains("lang=\"en\"");
        assertThat(flow.loginPage("error=PENDING").asString()).contains("not verified yet");
    }

    @Test
    void ac18_revokedRefreshTokenCannotBeUsedAndLogoutEndsTheSession() {
        AuthorizationCodeFlow flow = new AuthorizationCodeFlow();
        Response tokens = flow.exchange(flow.loginAndGetCode(DEAN, PASSWORD));
        String refreshToken = tokens.jsonPath().getString("refresh_token");
        String idToken = tokens.jsonPath().getString("id_token");

        AuthorizationCodeFlow.revoke(refreshToken, "refresh_token").then().statusCode(200);
        AuthorizationCodeFlow.refresh(refreshToken).then().statusCode(400).body("error", equalTo("invalid_grant"));

        Response logout = RestAssured.given().redirects().follow(false).cookies(flow.cookies())
            .queryParam("id_token_hint", idToken)
            .queryParam("post_logout_redirect_uri", "http://localhost:4200")
            .get("/connect/logout");
        assertThat(logout.getStatusCode()).isEqualTo(302);
        assertThat(logout.getHeader("Location")).startsWith("http://localhost:4200");

        Response afterLogout = RestAssured.given().redirects().follow(false).cookies(flow.cookies())
            .accept("text/html")
            .get("/oauth2/authorize?response_type=code&client_id=award-web&scope=openid"
                + "&redirect_uri=http://localhost:4200/callback&code_challenge=abc&code_challenge_method=S256");
        assertThat(afterLogout.getHeader("Location")).endsWith("/login");
    }

    @Test
    void ac19_publicPathsAreOpenAndTheApiRequiresAToken() {
        RestAssured.when().get("/.well-known/openid-configuration").then().statusCode(200)
            .body("issuer", equalTo("http://localhost:8080"));
        RestAssured.when().get("/oauth2/jwks").then().statusCode(200).body("keys[0].kty", equalTo("RSA"));
        RestAssured.when().get("/login").then().statusCode(200);
        RestAssured.when().get("/actuator/health").then().statusCode(200).body("status", equalTo("UP"));
        RestAssured.when().get("/v3/api-docs").then().statusCode(200)
            .body("components.securitySchemes.oauth2.flows.authorizationCode.tokenUrl", equalTo("http://localhost:8080/oauth2/token"))
            .body("components.securitySchemes.bearer.scheme", equalTo("bearer"));
        String initializer = RestAssured.when().get("/swagger-ui/swagger-initializer.js").then().statusCode(200)
            .extract().asString();
        assertThat(initializer).contains("award-web").contains("usePkceWithAuthorizationCodeGrant");
        RestAssured.given().redirects().follow(false).when().get("/swagger-ui.html").then().statusCode(302)
            .header("Location", startsWith("/swagger-ui"));

        RestAssured.when().get("/api/v1/users/me").then().statusCode(401)
            .contentType("application/problem+json");
        RestAssured.when().get("/actuator/metrics").then().statusCode(401);
        RestAssured.given().header("Authorization", "Bearer not-a-token").get("/api/v1/users/me")
            .then().statusCode(401);
    }

    @Test
    void ac62_ac63_directLoginLandsOnTheAppAndAStaleFormReturnsToTheLoginPage() {
        AuthorizationCodeFlow direct = new AuthorizationCodeFlow();
        assertThat(direct.submitLogin(DEAN, PASSWORD).getHeader("Location")).isEqualTo("http://localhost:4200");
        assertThat(direct.loginPage("").getHeader("Location")).isEqualTo("http://localhost:4200");
        RestAssured.given().redirects().follow(false).cookies(direct.cookies()).when().get("/")
            .then().statusCode(302).header("Location", "http://localhost:4200");
        RestAssured.given().redirects().follow(false).when().get("/")
            .then().statusCode(302).header("Location", "http://localhost:4200");

        RestAssured.given().redirects().follow(false)
            .formParam("username", DEAN).formParam("password", PASSWORD).formParam("_csrf", "stale")
            .post("/login").then().statusCode(302).header("Location", endsWith("/login?error=EXPIRED"));
        RestAssured.given().queryParam("error", "EXPIRED").when().get("/login").then().statusCode(200)
            .body(containsString("Сторінка застаріла"));
        RestAssured.given().redirects().follow(false).accept("text/html").formParam("_csrf", "stale")
            .post("/logout").then().statusCode(403).body(containsString("Щось пішло не так"))
            .body(containsString("href=\"/\""));

        Map<String, String> detour = new HashMap<>(RestAssured.given().redirects().follow(false).accept("text/html")
            .when().get("/favicon.ico").then().statusCode(302).extract().cookies());
        Response form = RestAssured.given().redirects().follow(false).cookies(detour).get("/login");
        detour.putAll(form.getCookies());
        Matcher csrf = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(form.asString());
        assertThat(csrf.find()).isTrue();
        RestAssured.given().redirects().follow(false).cookies(detour)
            .formParam("username", DEAN).formParam("password", PASSWORD).formParam("_csrf", csrf.group(1))
            .post("/login").then().statusCode(302).header("Location", "http://localhost:4200");
    }
}
