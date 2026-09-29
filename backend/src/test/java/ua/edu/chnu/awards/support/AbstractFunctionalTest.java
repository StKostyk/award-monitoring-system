package ua.edu.chnu.awards.support;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;

import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;
import ua.edu.chnu.awards.user.repository.UserRoleRepository;

/**
 * Base class for functional API tests: signs accounts in through the browser flow and calls the API as them.
 */
public abstract class AbstractFunctionalTest extends AbstractIntegrationTest {

    /** Password of every account created with {@link TestUsers#user(String, Organization)}. */
    protected static final String DEMO_PASSWORD = "Passw0rd-demo";

    @Autowired
    private UserRepository accounts;

    @Autowired
    private UserRoleRepository roles;

    /**
     * Creates an account holding one role that became active yesterday.
     *
     * @param email account email
     * @param home  home organization of the account
     * @param role  role to grant
     * @param scope organization the role is scoped to
     * @return id of the new account
     */
    protected long withRole(String email, Organization home, RoleType role, Organization scope) {
        User user = accounts.save(TestUsers.user(email, home));
        roles.save(TestUsers.role(user, role, scope, LocalDate.now().minusDays(1), null));
        return user.getId();
    }

    /**
     * Signs an account in with the demo password and returns its access token.
     *
     * @param email account email
     * @return the access token
     */
    protected static String tokenOf(String email) {
        AuthorizationCodeFlow flow = new AuthorizationCodeFlow();
        return flow.exchange(flow.loginAndGetCode(email, DEMO_PASSWORD)).jsonPath().getString("access_token");
    }

    /**
     * Starts an API request carrying a bearer token.
     *
     * @param token access token
     * @return the request specification
     */
    protected static RequestSpecification as(String token) {
        return RestAssured.given().header("Authorization", "Bearer " + token);
    }

    /**
     * Decodes the claims of an access token.
     *
     * @param token access token
     * @return the claims
     */
    protected static Map<String, Object> claims(String token) {
        return AuthorizationCodeFlow.claimsOf(token);
    }
}
