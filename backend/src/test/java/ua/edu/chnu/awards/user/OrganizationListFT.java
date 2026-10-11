package ua.edu.chnu.awards.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

import java.time.Instant;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import io.restassured.RestAssured;
import io.restassured.response.Response;

import ua.edu.chnu.awards.auth.security.RateLimitFilter;
import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.RoleType;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrganizationListFT extends AbstractFunctionalTest {

    private static final String ORGANIZATIONS = "/api/v1/organizations?type=FACULTY";
    private static final String MEMBER = "ft.organizations.member@chnu.edu.ua";
    private static final String BURST_IP = "10.98.1.7";
    private static final String FRESH_IP = "10.98.1.8";

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Value("${app.auth.protection.public-requests-per-minute}")
    private int publicRequestsPerMinute;

    @BeforeAll
    void createMember() {
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        withRole(MEMBER, department, RoleType.EMPLOYEE, department);
    }

    @AfterAll
    void deleteMember() {
        userRepository.findByEmailAddressIgnoreCase(MEMBER).ifPresent(userRepository::delete);
    }

    @Test
    void ac1_anonymousCallsCountAgainstThePublicLimit() {
        RestAssured.given().header("X-Forwarded-For", FRESH_IP).get(ORGANIZATIONS).then()
            .statusCode(HttpStatus.OK.value());
        assertThat(redis.keys(RateLimitFilter.PUBLIC_KEY_PREFIX + FRESH_IP + ":*")).isNotEmpty();

        exhaust(BURST_IP);
        Response refused = RestAssured.given().header("X-Forwarded-For", BURST_IP).get(ORGANIZATIONS);

        refused.then().statusCode(HttpStatus.TOO_MANY_REQUESTS.value())
            .header(HttpHeaders.RETRY_AFTER, notNullValue())
            .contentType("application/problem+json")
            .body("type", equalTo("urn:awards:problem:too-many-requests"));
        assertThat(Integer.parseInt(refused.getHeader(HttpHeaders.RETRY_AFTER))).isBetween(1, 60);
    }

    @Test
    void ac2_signedInCallsKeepTheirBehaviourOnAnExhaustedAddress() {
        exhaust(BURST_IP);

        as(tokenOf(MEMBER)).header("X-Forwarded-For", BURST_IP).get(ORGANIZATIONS).then()
            .statusCode(HttpStatus.OK.value())
            .body("code", hasItem("FMI"));
        as("not.a.token").header("X-Forwarded-For", BURST_IP).get(ORGANIZATIONS).then()
            .statusCode(HttpStatus.UNAUTHORIZED.value());
    }

    private void exhaust(String address) {
        long window = Instant.now().getEpochSecond() / 60;
        String used = String.valueOf(publicRequestsPerMinute);
        redis.opsForValue().set(RateLimitFilter.PUBLIC_KEY_PREFIX + address + ":" + window, used);
        redis.opsForValue().set(RateLimitFilter.PUBLIC_KEY_PREFIX + address + ":" + (window + 1), used);
    }
}
