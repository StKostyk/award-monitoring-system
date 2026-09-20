package ua.edu.chnu.awards;

import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import io.restassured.RestAssured;

import ua.edu.chnu.awards.support.AbstractIntegrationTest;

class ActuatorHealthFT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void healthReportsUpWithDatabaseAndRedis() {
        RestAssured.given()
            .auth().basic("test", "test")
            .when()
            .get("/actuator/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("components.db.status", equalTo("UP"))
            .body("components.redis.status", equalTo("UP"));
    }

    @Test
    void protectedEndpointRequiresAuthentication() {
        RestAssured.when()
            .get("/actuator/metrics")
            .then()
            .statusCode(401);
    }
}
