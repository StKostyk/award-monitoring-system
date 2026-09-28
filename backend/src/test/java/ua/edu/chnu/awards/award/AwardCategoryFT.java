package ua.edu.chnu.awards.award;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import io.restassured.RestAssured;
import io.restassured.response.Response;

import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.AuthorizationCodeFlow;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AwardCategoryFT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Passw0rd-demo";
    private static final String NEWCOMER = "ft.categories.newcomer@chnu.edu.ua";
    private static final String PATH = "/api/v1/award-categories";

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @BeforeAll
    void createUser() {
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        userRepository.save(TestUsers.user(NEWCOMER, department));
    }

    @AfterAll
    void deleteUser() {
        userRepository.findByEmailAddressIgnoreCase(NEWCOMER).ifPresent(userRepository::delete);
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void ac02_ac03_anySignedInUserReadsTheCatalogueTreeWithCachingHeaders() {
        AuthorizationCodeFlow flow = new AuthorizationCodeFlow();
        String token = flow.exchange(flow.loginAndGetCode(NEWCOMER, PASSWORD)).jsonPath().getString("access_token");

        Response response = RestAssured.given().header("Authorization", "Bearer " + token).get(PATH);
        response.then().statusCode(200)
            .header("Cache-Control", "max-age=3600")
            .header("ETag", notNullValue())
            .body("", hasSize(9))
            .body("level", hasItems("SPECIALITY", "COLLEGE", "LOCAL", "REGIONAL", "INTERNATIONAL"))
            .body("collect { it.children.size() }", everyItem(greaterThanOrEqualTo(2)))
            .body("find { it.id == 10 }.nameUk", equalTo("Національні нагороди"))
            .body("find { it.id == 10 }.children.find { it.id == 13 }.name", equalTo("Ministry Recognition"));

        RestAssured.given().header("Authorization", "Bearer " + token)
            .header("If-None-Match", response.header("ETag")).get(PATH)
            .then().statusCode(304).header("ETag", equalTo(response.header("ETag")));
    }

    @Test
    void ac02_withoutATokenTheCatalogueIsRefused() {
        RestAssured.given().get(PATH).then().statusCode(401);
    }
}
