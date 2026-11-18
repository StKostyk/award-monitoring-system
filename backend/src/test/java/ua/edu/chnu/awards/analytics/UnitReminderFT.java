package ua.edu.chnu.awards.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

import ua.edu.chnu.awards.analytics.service.UnitAggregates;
import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.AwardRows;
import ua.edu.chnu.awards.support.ConsentRows;
import ua.edu.chnu.awards.support.Eventually;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UnitReminderFT extends AbstractFunctionalTest {

    private static final String UNITS = "/api/v1/analytics/units/";
    private static final String PROBLEM = "urn:awards:problem:";
    private static final String PREFIX = "ft.reminder.";
    private static final String DOMAIN = "@chnu.edu.ua";
    private static final String SECRETARY = PREFIX + "secretary" + DOMAIN;
    private static final String DEAN = PREFIX + "dean" + DOMAIN;
    private static final String RECTOR_SECRETARY = PREFIX + "rector-secretary" + DOMAIN;
    private static final String EMPLOYEE = PREFIX + "employee" + DOMAIN;
    private static final String MUSIC_MEMBER = PREFIX + "music" + DOMAIN;
    private static final String PRIMARY_MEMBER = PREFIX + "primary" + DOMAIN;
    private static final String PRESCHOOL_MEMBER = PREFIX + "preschool" + DOMAIN;
    private static final String SUBJECT = "Нагадування про нагороди";
    private static final long UNIVERSITY_ID = 1L;
    private static final long FACULTY_ID = 10L;
    private static final long MUSIC_ID = 70L;
    private static final long PRIMARY_ID = 71L;
    private static final long PRESCHOOL_ID = 72L;
    private static final long OTHER_DEPARTMENT_ID = 64L;
    private static final long PSYCHOLOGY_ID = 73L;
    private static final Duration POLL = Duration.ofMillis(200);
    private static final int CURRENT_YEAR = LocalDate.now(ZoneId.of("Europe/Kyiv")).getYear();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private CacheManager caches;

    @Autowired
    private JdbcTemplate jdbc;

    private long musicMemberId;
    private long primaryMemberId;
    private long preschoolMemberId;

    @BeforeAll
    void createAccounts() {
        Organization university = organizationRepository.findById(UNIVERSITY_ID).orElseThrow();
        Organization faculty = organizationRepository.findById(FACULTY_ID).orElseThrow();
        withRole(SECRETARY, faculty, RoleType.FACULTY_SECRETARY, faculty);
        withRole(DEAN, faculty, RoleType.DEAN, faculty);
        withRole(RECTOR_SECRETARY, university, RoleType.RECTOR_SECRETARY, university);
        Organization music = organizationRepository.findById(MUSIC_ID).orElseThrow();
        withRole(EMPLOYEE, music, RoleType.EMPLOYEE, music);
        musicMemberId = withRole(MUSIC_MEMBER, music, RoleType.EMPLOYEE, music);
        Organization primary = organizationRepository.findById(PRIMARY_ID).orElseThrow();
        primaryMemberId = withRole(PRIMARY_MEMBER, primary, RoleType.EMPLOYEE, primary);
        Organization preschool = organizationRepository.findById(PRESCHOOL_ID).orElseThrow();
        preschoolMemberId = withRole(PRESCHOOL_MEMBER, preschool, RoleType.EMPLOYEE, preschool);
        AwardRows.award(jdbc, primaryMemberId).unit(PRIMARY_ID).status("APPROVED")
            .awardDate(LocalDate.of(CURRENT_YEAR, 1, 10)).insert();
        Objects.requireNonNull(caches.getCache(UnitAggregates.CACHE)).clear();
    }

    @AfterAll
    void deleteAccounts() {
        String owners = " (select user_id from users where email_address like '" + PREFIX + "%')";
        jdbc.update("delete from notifications where user_id in" + owners);
        jdbc.update("delete from awards where user_id in" + owners);
        userRepository.findAll().stream().filter(user -> user.getEmailAddress().startsWith(PREFIX))
            .forEach(user -> {
                ConsentRows.purge(jdbc, user.getId());
                userRepository.delete(user);
            });
        Objects.requireNonNull(caches.getCache(UnitAggregates.CACHE)).clear();
    }

    @Test
    void ac5_3_ac5_4_theSecretaryRemindsOnceAndTheDeanIsRefusedWithTheDate() {
        remind(tokenOf(SECRETARY), MUSIC_ID, musicMemberId).then().statusCode(HttpStatus.ACCEPTED.value())
            .body("unit.id", equalTo((int) MUSIC_ID)).body("recipient.id", equalTo((int) musicMemberId))
            .body("sentAt", notNullValue());

        String mail = mailpit.latestTextTo(MUSIC_MEMBER, SUBJECT);
        assertThat(mail).contains("Кафедра музики", "Department of Music", String.valueOf(CURRENT_YEAR));
        Map<String, Object> row = Eventually.attempt(() -> jdbc.queryForList(
                "select payload::text as payload from notifications where user_id = ? and type = 'AWARDS_REMINDER' "
                    + "and channel = 'IN_APP'", musicMemberId).stream().findFirst(), Eventually.LISTENER_WAIT, POLL)
            .orElseThrow();
        assertThat((String) row.get("payload")).contains("\"unitId\": " + MUSIC_ID, "\"year\": " + CURRENT_YEAR);

        remind(tokenOf(DEAN), MUSIC_ID, musicMemberId).then().statusCode(HttpStatus.CONFLICT.value())
            .body("type", equalTo(PROBLEM + "recently-reminded")).body("lastSentAt", notNullValue());
        as(tokenOf(SECRETARY)).get(UNITS + FACULTY_ID).then().statusCode(HttpStatus.OK.value())
            .body("units.find { it.unit.id == " + MUSIC_ID + " }.lastReminderAt", notNullValue())
            .body("units.find { it.unit.id == " + PRIMARY_ID + " }.lastReminderAt", nullValue());
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action_type = 'UNIT_REMINDED' "
            + "and entity_type = 'organizations' and entity_id = ?", Long.class, MUSIC_ID)).isEqualTo(1L);
    }

    @Test
    void ac5_5_scopeRecipientAndAwardsAreCheckedAndNothingIsSent() {
        String secretary = tokenOf(SECRETARY);
        remind(secretary, OTHER_DEPARTMENT_ID, musicMemberId).then().statusCode(HttpStatus.FORBIDDEN.value());
        remind(secretary, 999_999L, musicMemberId).then().statusCode(HttpStatus.NOT_FOUND.value());
        remind(secretary, PSYCHOLOGY_ID, musicMemberId).then().statusCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .body("type", equalTo(PROBLEM + "invalid-recipient"));
        remind(secretary, PRIMARY_ID, primaryMemberId).then().statusCode(HttpStatus.CONFLICT.value())
            .body("type", equalTo(PROBLEM + "has-awards"));
        remind(secretary, FACULTY_ID, primaryMemberId).then().statusCode(HttpStatus.CONFLICT.value())
            .body("type", equalTo(PROBLEM + "has-awards"));
        as(secretary).contentType(ContentType.JSON).body(Map.of()).post(UNITS + PRESCHOOL_ID + "/reminders").then()
            .statusCode(HttpStatus.BAD_REQUEST.value()).body("parameter", equalTo("recipientId"));
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action_type = 'UNIT_REMINDED' "
            + "and entity_id in (?, ?, ?)", Long.class, OTHER_DEPARTMENT_ID, PRIMARY_ID, FACULTY_ID)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ?", Long.class,
            primaryMemberId)).isZero();
    }

    @Test
    void ac5_6_theRectorsSecretaryAndAnEmployeeCannotRemind() {
        remind(tokenOf(RECTOR_SECRETARY), PRESCHOOL_ID, preschoolMemberId).then()
            .statusCode(HttpStatus.FORBIDDEN.value());
        remind(tokenOf(EMPLOYEE), PRESCHOOL_ID, preschoolMemberId).then().statusCode(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void ac5_4_twoSendersAtOnceGetOneAcceptedAndOneConflict() throws Exception {
        String secretary = tokenOf(SECRETARY);
        String dean = tokenOf(DEAN);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Integer>> senders = new ArrayList<>();
        for (String token : List.of(secretary, dean)) {
            senders.add(() -> {
                start.await();
                return remind(token, PRESCHOOL_ID, preschoolMemberId).statusCode();
            });
        }
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = senders.stream().map(pool::submit).toList();
            start.countDown();
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> result : results) {
                codes.add(result.get());
            }
            assertThat(codes).containsExactlyInAnyOrder(HttpStatus.ACCEPTED.value(), HttpStatus.CONFLICT.value());
        } finally {
            pool.shutdownNow();
        }
    }

    private static Response remind(String token, long unitId, long recipientId) {
        return as(token).contentType(ContentType.JSON).body(Map.of("recipientId", recipientId))
            .post(UNITS + unitId + "/reminders");
    }
}
