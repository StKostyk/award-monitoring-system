package ua.edu.chnu.awards.gdpr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.RestAssured;

import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.common.web.PageResponse;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ComplianceFT extends AbstractFunctionalTest {

    private static final String OFFICER = "ft.compliance.officer@chnu.edu.ua";
    private static final String ADMIN = "ft.compliance.admin@chnu.edu.ua";
    private static final String DEAN = "ft.compliance.dean@chnu.edu.ua";
    private static final String PERSON = "ft.compliance.person@chnu.edu.ua";
    private static final List<String> ACCOUNTS = List.of(OFFICER, ADMIN, DEAN, PERSON);
    private static final List<String> ENDPOINTS = List.of("/api/v1/compliance/retention-policies",
        "/api/v1/compliance/retention-runs", "/api/v1/compliance/erasure-requests",
        "/api/v1/compliance/legal-holds");
    private static final int POLICY_COUNT = 9;
    private static final Instant FAR_AHEAD = Instant.parse("2099-03-01T02:00:00Z");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long personId;
    private long officerId;

    @BeforeAll
    void createUsers() {
        Organization university = organizationRepository.findById(TestUsers.UNIVERSITY_ID).orElseThrow();
        officerId = withRole(OFFICER, university, RoleType.GDPR_OFFICER, university);
        withRole(ADMIN, university, RoleType.SYSTEM_ADMIN, university);
        Organization faculty = organizationRepository.findById(TestUsers.FMI_FACULTY_ID).orElseThrow();
        withRole(DEAN, faculty, RoleType.DEAN, faculty);
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        personId = withRole(PERSON, department, RoleType.EMPLOYEE, department);
    }

    @AfterAll
    void deleteUsers() {
        jdbc.update("delete from retention_runs where started_at >= ?", Timestamp.from(FAR_AHEAD));
        jdbc.update("delete from erasure_requests where user_id = ?", personId);
        jdbc.update("delete from legal_holds where user_id = ?", personId);
        ACCOUNTS.forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @Test
    void ac2_1_theOfficerReadsThePoliciesInRunOrder() {
        List<Integer> orders = as(tokenOf(OFFICER)).get(ENDPOINTS.getFirst())
            .then().statusCode(HttpStatus.OK.value())
            .body("$", hasSize(POLICY_COUNT))
            .body("[0].key", equalTo("notifications"))
            .body("find { it.key == 'pending-accounts' }.action", equalTo("erase-account"))
            .body("find { it.key == 'audit-partitions' }.action", equalTo("create-partitions"))
            .body("find { it.key == 'audit-logs' }.action", equalTo("drop-partitions"))
            .body("find { it.key == 'audit-logs' }.period", equalTo("P7Y"))
            .extract().jsonPath().getList("order");

        assertThat(orders).isSorted();
    }

    @Test
    void ac2_1_everyoneElseIsRefused() {
        for (String endpoint : ENDPOINTS) {
            for (String email : List.of(ADMIN, DEAN, PERSON)) {
                as(tokenOf(email)).get(endpoint).then().statusCode(HttpStatus.FORBIDDEN.value());
            }
            RestAssured.given().get(endpoint).then().statusCode(HttpStatus.UNAUTHORIZED.value());
        }
    }

    @Test
    void ac2_2_runsAreListedNewestFirstWithAClampedPageSize() {
        jdbc.update("insert into retention_runs (started_at, finished_at, status, results) values (?, ?, 'COMPLETED',"
            + " '[{\"policy\":\"devices\",\"affected\":2,\"held\":0,\"failed\":false,\"error\":null}]')",
            Timestamp.from(FAR_AHEAD), Timestamp.from(FAR_AHEAD.plusSeconds(3)));

        as(tokenOf(OFFICER)).queryParam("size", 500).get(ENDPOINTS.get(1))
            .then().statusCode(HttpStatus.OK.value())
            .body("size", equalTo(PageResponse.MAX_SIZE))
            .body("content[0].status", equalTo("COMPLETED"))
            .body("content[0].startedAt", equalTo("2099-03-01T02:00:00Z"))
            .body("content[0].finishedAt", equalTo("2099-03-01T02:00:03Z"))
            .body("content[0].results[0].policy", equalTo("devices"))
            .body("content[0].results[0].affected", equalTo(2))
            .body("content[0].results[0].error", nullValue());
    }

    @Test
    void ac2_3_requestsAreFilteredByStatusAndAnUnknownStatusIsRejected() {
        jdbc.update("insert into erasure_requests (user_id, requested_at, scheduled_for) values (?, ?, ?)",
            personId, Timestamp.from(FAR_AHEAD), Timestamp.from(FAR_AHEAD.plusSeconds(60)));

        as(tokenOf(OFFICER)).queryParam("status", "PENDING").get(ENDPOINTS.get(2))
            .then().statusCode(HttpStatus.OK.value())
            .body("content.status", everyItem(equalTo("PENDING")))
            .body("content[0].person.id", equalTo((int) personId))
            .body("content[0].origin", equalTo("REQUEST"))
            .body("content[0].requestedAt", equalTo("2099-03-01T02:00:00Z"))
            .body("content[0].completedAt", nullValue());
        as(tokenOf(OFFICER)).queryParam("status", "COMPLETED").get(ENDPOINTS.get(2))
            .then().statusCode(HttpStatus.OK.value())
            .body("content.person.id", not(hasItem((int) personId)));
        as(tokenOf(OFFICER)).queryParam("status", "POSTPONED").get(ENDPOINTS.get(2))
            .then().statusCode(HttpStatus.BAD_REQUEST.value())
            .body("type", endsWith("invalid-parameter"))
            .body("parameter", equalTo("status"));
    }

    @Test
    void ac2_2_ac2_4_pagesOutOfRangeAreClampedAndMalformedParametersAreRejected() {
        as(tokenOf(OFFICER)).queryParam("page", -1).queryParam("size", 0).get(ENDPOINTS.get(1))
            .then().statusCode(HttpStatus.OK.value())
            .body("number", equalTo(0))
            .body("size", equalTo(1));
        as(tokenOf(OFFICER)).queryParam("size", "abc").get(ENDPOINTS.get(2))
            .then().statusCode(HttpStatus.BAD_REQUEST.value())
            .body("type", endsWith("invalid-parameter"))
            .body("parameter", equalTo("size"));
        as(tokenOf(OFFICER)).queryParam("active", "maybe").get(ENDPOINTS.get(3))
            .then().statusCode(HttpStatus.BAD_REQUEST.value())
            .body("type", endsWith("invalid-parameter"))
            .body("parameter", equalTo("active"));
    }

    @Test
    void ac2_4_activeHoldsAreListedWithThePersonAndTheOfficer() {
        jdbc.update("insert into legal_holds (user_id, reason, placed_by, placed_at, lifted_by, lifted_at,"
            + " lift_reason) values (?, 'Audit 2099', ?, ?, ?, ?, 'Closed')", personId, officerId,
            Timestamp.from(FAR_AHEAD), officerId, Timestamp.from(FAR_AHEAD.plusSeconds(60)));
        jdbc.update("insert into legal_holds (user_id, reason, placed_by, placed_at) values (?, 'Case 2099', ?, ?)",
            personId, officerId, Timestamp.from(FAR_AHEAD.plusSeconds(120)));

        as(tokenOf(OFFICER)).get(ENDPOINTS.get(3))
            .then().statusCode(HttpStatus.OK.value())
            .body("content[0].reason", equalTo("Case 2099"))
            .body("content[1].reason", equalTo("Audit 2099"))
            .body("content[1].liftedBy.id", equalTo((int) officerId))
            .body("content[1].liftReason", equalTo("Closed"));
        as(tokenOf(OFFICER)).queryParam("active", true).get(ENDPOINTS.get(3))
            .then().statusCode(HttpStatus.OK.value())
            .body("content.liftedAt", everyItem(nullValue()))
            .body("content[0].reason", equalTo("Case 2099"))
            .body("content[0].person.id", equalTo((int) personId))
            .body("content[0].placedBy.id", equalTo((int) officerId));
    }
}
