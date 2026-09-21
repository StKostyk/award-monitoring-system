package ua.edu.chnu.awards.support;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.util.UriComponentsBuilder;

import io.restassured.RestAssured;
import io.restassured.response.Response;

/**
 * Drives the authorization code flow with PKCE against the booted application the way the browser client does.
 */
public final class AuthorizationCodeFlow {

    public static final String CLIENT_ID = "award-web";
    public static final String REDIRECT_URI = "http://localhost:4200/callback";

    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RANDOM_BYTES = 32;

    private final Map<String, String> cookies = new HashMap<>();
    private final Map<String, String> headers = new HashMap<>();
    private final String verifier = randomUrlSafe();
    private final String state = randomUrlSafe();

    /**
     * Adds a header to every browser-side request of this flow (user agent, accepted languages).
     *
     * @param name  header name
     * @param value header value
     * @return this flow
     */
    public AuthorizationCodeFlow header(String name, String value) {
        headers.put(name, value);
        return this;
    }

    /**
     * Starts the flow: the authorize request must bounce to the login page.
     *
     * @return the authorize response (302 to /login for an anonymous browser)
     */
    public Response authorize() {
        Response response = RestAssured.given().redirects().follow(false).headers(headers).cookies(cookies)
            .accept("text/html")
            .queryParam("response_type", "code")
            .queryParam("client_id", CLIENT_ID)
            .queryParam("redirect_uri", REDIRECT_URI)
            .queryParam("scope", "openid profile")
            .queryParam("state", state)
            .queryParam("code_challenge", challenge(verifier))
            .queryParam("code_challenge_method", "S256")
            .get("/oauth2/authorize");
        remember(response);
        return response;
    }

    /**
     * Loads the login page (keeping the session) and returns its HTML.
     *
     * @param query extra query string such as {@code lang=en}, may be empty
     * @return response of the login page
     */
    public Response loginPage(String query) {
        Response response = RestAssured.given().redirects().follow(false).headers(headers).cookies(cookies)
            .get("/login" + (query.isEmpty() ? "" : "?" + query));
        remember(response);
        return response;
    }

    /**
     * Submits the login form.
     *
     * @param email    address
     * @param password password
     * @return the redirect after login (to the saved authorize request, or back to /login with an error)
     */
    public Response submitLogin(String email, String password) {
        String csrf = csrfToken(loginPage("").asString());
        Response response = RestAssured.given().redirects().follow(false).headers(headers).cookies(cookies)
            .formParam("username", email)
            .formParam("password", password)
            .formParam("_csrf", csrf)
            .post("/login");
        remember(response);
        return response;
    }

    /**
     * Logs in and completes the authorize request.
     *
     * @param email    address
     * @param password password
     * @return the authorization code sent to the redirect URI
     */
    public String loginAndGetCode(String email, String password) {
        authorize();
        Response afterLogin = submitLogin(email, password);
        Response redirect = RestAssured.given().redirects().follow(false).headers(headers).cookies(cookies)
            .accept("text/html")
            .urlEncodingEnabled(false)
            .get(afterLogin.getHeader("Location"));
        remember(redirect);
        String location = redirect.getHeader("Location");
        if (location == null || !location.startsWith(REDIRECT_URI)) {
            throw new IllegalStateException("Expected redirect to the client, got " + redirect.getStatusCode()
                + " " + location);
        }
        Map<String, String> params = new HashMap<>();
        UriComponentsBuilder.fromUri(URI.create(location)).build().getQueryParams()
            .forEach((k, v) -> params.put(k, v.get(0)));
        if (!state.equals(params.get("state"))) {
            throw new IllegalStateException("State mismatch");
        }
        return params.get("code");
    }

    /**
     * Exchanges the code for tokens using the PKCE verifier.
     *
     * @param code the authorization code
     * @return the token endpoint response
     */
    public Response exchange(String code) {
        return exchange(code, verifier);
    }

    /**
     * Exchanges the code with an arbitrary verifier (null omits the parameter).
     *
     * @param code         the authorization code
     * @param codeVerifier the PKCE verifier to present
     * @return the token endpoint response
     */
    public Response exchange(String code, String codeVerifier) {
        var request = RestAssured.given()
            .formParam("grant_type", "authorization_code")
            .formParam("code", code)
            .formParam("redirect_uri", REDIRECT_URI)
            .formParam("client_id", CLIENT_ID);
        if (codeVerifier != null) {
            request.formParam("code_verifier", codeVerifier);
        }
        return request.post("/oauth2/token");
    }

    /**
     * Uses a refresh token.
     *
     * @param refreshToken the refresh token
     * @return the token endpoint response
     */
    public static Response refresh(String refreshToken) {
        return RestAssured.given()
            .formParam("grant_type", "refresh_token")
            .formParam("refresh_token", refreshToken)
            .formParam("client_id", CLIENT_ID)
            .post("/oauth2/token");
    }

    /**
     * Revokes a token.
     *
     * @param token         the token value
     * @param tokenTypeHint {@code refresh_token} or {@code access_token}
     * @return the revocation response
     */
    public static Response revoke(String token, String tokenTypeHint) {
        return RestAssured.given()
            .formParam("token", token)
            .formParam("token_type_hint", tokenTypeHint)
            .formParam("client_id", CLIENT_ID)
            .post("/oauth2/revoke");
    }

    public Map<String, String> cookies() {
        return Map.copyOf(cookies);
    }

    private void remember(Response response) {
        cookies.putAll(response.getCookies());
    }

    static String csrfToken(String html) {
        Matcher matcher = CSRF.matcher(html);
        if (!matcher.find()) {
            throw new IllegalStateException("No CSRF token in login page");
        }
        return matcher.group(1);
    }

    static String challenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String randomUrlSafe() {
        byte[] bytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
