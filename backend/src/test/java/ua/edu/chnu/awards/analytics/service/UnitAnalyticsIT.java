package ua.edu.chnu.awards.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import ua.edu.chnu.awards.analytics.dto.TopPerformers;
import ua.edu.chnu.awards.analytics.dto.UnitAnalytics;
import ua.edu.chnu.awards.analytics.dto.UnitComparisonRow;
import ua.edu.chnu.awards.authz.TokenClaimConstants;
import ua.edu.chnu.awards.organization.OrganizationTree;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.ConsentRows;

@ExtendWith(OutputCaptureExtension.class)
class UnitAnalyticsIT extends AbstractIntegrationTest {

    private static final long FACULTY_ID = 2L;
    private static final long FIRST_DEPARTMENT_ID = 20L;
    private static final long SECOND_DEPARTMENT_ID = 21L;
    private static final long SMALL_DEPARTMENT_ID = 22L;
    private static final long OTHER_FACULTY_DEPARTMENT_ID = 26L;
    private static final int MIN_GROUP_SIZE = 5;
    private static final long MINISTRY = 13L;
    private static final long FACULTY_TEACHING = 31L;
    private static final String MINISTRY_NAME = "Міністерство освіти і науки України";
    private static final String INSERT_USER = "insert into users (email_address, first_name, last_name, "
        + "password_hash, account_status, organization_id) values (?, ?, ?, 'x', ?, ?) returning user_id";
    private static final String INSERT_AWARD = "insert into awards (user_id, organization_id, recipient_org_id, "
        + "title, status, category_id, awarding_organization, award_date, verification_badge) "
        + "values (?, ?, ?, 'IT unit award', ?, ?, ?, ?, ?)";

    @Autowired
    private UnitAnalyticsService analytics;

    @Autowired
    private TopPerformersRanking ranking;

    @Autowired
    private UnitAggregates aggregates;

    @Autowired
    private CacheManager caches;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private OrganizationTree tree;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;

    @BeforeEach
    void setUp() {
        Objects.requireNonNull(caches.getCache(UnitAggregates.CACHE)).clear();
        Jwt jwt = Jwt.withTokenValue("it").header("alg", "none").subject("1")
            .claim(TokenClaimConstants.CLAIM_HELD_SCOPES, List.of("RECTOR:1")).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
            AuthorityUtils.createAuthorityList("analytics:read:faculty")));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        jdbc.query("select user_id from users where email_address like 'it.units.%'",
            (RowCallbackHandler) row -> ConsentRows.purge(jdbc, row.getLong(1)));
        jdbc.update("delete from awards where user_id in (select user_id from users where email_address like "
            + "'it.units.%')");
        jdbc.update("delete from users where email_address like 'it.units.%'");
        Objects.requireNonNull(caches.getCache(UnitAggregates.CACHE)).clear();
    }

    @Test
    void ac1_4_onlyApprovedAwardsSubmittedInTheSubtreeAndPeriodCountWhereverTheOwnerWorksNow() {
        final long members = activeMembers(FACULTY_ID);
        long first = user("it.units.first", FIRST_DEPARTMENT_ID);
        final long second = user("it.units.second", SECOND_DEPARTMENT_ID);
        final long moved = user("it.units.moved", FIRST_DEPARTMENT_ID);
        award(first, FIRST_DEPARTMENT_ID, false, "APPROVED", LocalDate.of(2024, 3, 1), MINISTRY_NAME, true);
        award(first, FIRST_DEPARTMENT_ID, false, "PENDING", LocalDate.of(2024, 4, 1), MINISTRY_NAME, false);
        award(first, FIRST_DEPARTMENT_ID, false, "DRAFT", LocalDate.of(2024, 5, 1), MINISTRY_NAME, false);
        award(first, FIRST_DEPARTMENT_ID, false, "REJECTED", LocalDate.of(2024, 6, 1), MINISTRY_NAME, false);
        award(first, FIRST_DEPARTMENT_ID, false, "APPROVED", LocalDate.of(2019, 6, 1), MINISTRY_NAME, false);
        award(first, FIRST_DEPARTMENT_ID, false, "APPROVED", LocalDate.of(2016, 6, 1), MINISTRY_NAME, false);
        award(second, SECOND_DEPARTMENT_ID, false, "APPROVED", LocalDate.of(2025, 2, 1),
            " міністерство  освіти і науки україни", false);
        award(first, FIRST_DEPARTMENT_ID, true, "APPROVED", LocalDate.of(2023, 9, 1), "Обласна адміністрація", false);
        award(first, FACULTY_ID, true, "APPROVED", LocalDate.of(2026, 1, 15), "Обласна адміністрація", false);
        award(moved, FIRST_DEPARTMENT_ID, false, "APPROVED", LocalDate.of(2024, 8, 1), MINISTRY_NAME, false);
        award(second, OTHER_FACULTY_DEPARTMENT_ID, false, "APPROVED", LocalDate.of(2024, 8, 1), MINISTRY_NAME,
            false);
        jdbc.update("update users set organization_id = ? where user_id = ?", OTHER_FACULTY_DEPARTMENT_ID, moved);

        UnitAnalytics result = analytics.of(FACULTY_ID, 2022, 2026);

        assertThat(result.totals().approved().current()).isEqualTo(5);
        assertThat(result.totals().approved().previous()).isEqualTo(1);
        assertThat(result.totals().awardedPeople().current()).isEqualTo(3);
        assertThat(result.totals().awardedPeople().previous()).isEqualTo(1);
        assertThat(result.totals().personal()).isEqualTo(3);
        assertThat(result.totals().unitAwards()).isEqualTo(2);
        assertThat(result.totals().verified()).isEqualTo(1);
        assertThat(result.totals().members()).isEqualTo(members + 2);
        assertThat(result.bySource().sources()).extracting(source -> source.name(), source -> source.count())
            .containsExactly(tuple(MINISTRY_NAME, 3L), tuple("Обласна адміністрація", 2L));
        assertThat(result.byYear()).extracting(year -> year.year(), year -> year.personal(), year -> year.unitAwards())
            .containsExactly(tuple(2022, 0L, 0L), tuple(2023, 0L, 1L), tuple(2024, 2L, 0L), tuple(2025, 1L, 0L),
                tuple(2026, 0L, 1L));
    }

    @Test
    void ac1_5_theDepartmentRowsAndTheFacultyLevelAddUpToTheFaculty() {
        long first = user("it.units.first", FIRST_DEPARTMENT_ID);
        award(first, FIRST_DEPARTMENT_ID, false, "APPROVED", LocalDate.of(2024, 3, 1), MINISTRY_NAME, false);
        award(first, SECOND_DEPARTMENT_ID, false, "APPROVED", LocalDate.of(2025, 3, 1), MINISTRY_NAME, false);
        award(first, FACULTY_ID, true, "APPROVED", LocalDate.of(2026, 1, 15), MINISTRY_NAME, false);

        UnitAnalytics result = analytics.of(FACULTY_ID, 2022, 2026);

        assertThat(result.units()).filteredOn(UnitComparisonRow::facultyLevel)
            .extracting(row -> row.unit().id(), row -> row.approved().current())
            .containsExactly(tuple(FACULTY_ID, 1L));
        assertThat(result.units()).filteredOn(row -> !row.facultyLevel())
            .extracting(row -> row.unit().id()).contains(FIRST_DEPARTMENT_ID, SECOND_DEPARTMENT_ID)
            .doesNotContain(OTHER_FACULTY_DEPARTMENT_ID);
        assertThat(result.units().stream().mapToLong(row -> row.approved().current()).sum())
            .isEqualTo(result.totals().approved().current());
        assertThat(analytics.of(FIRST_DEPARTMENT_ID, 2022, 2026).units()).isEmpty();
    }

    @Test
    void ac1_7_peopleAreListedFromTheMinimumGroupWithTheirConsentOnly() {
        final long baseline = activeMembers(SMALL_DEPARTMENT_ID);
        long named = user("it.units.named", SMALL_DEPARTMENT_ID);
        long silent = user("it.units.silent", SMALL_DEPARTMENT_ID);
        grantAnalytics(named);
        award(named, SMALL_DEPARTMENT_ID, false, "APPROVED", LocalDate.of(2025, 3, 1), MINISTRY_NAME, false);
        award(silent, SMALL_DEPARTMENT_ID, false, "APPROVED", LocalDate.of(2025, 3, 1), MINISTRY_NAME, false);
        for (long index = baseline + 2; index < MIN_GROUP_SIZE - 1; index++) {
            user("it.units.member" + index, SMALL_DEPARTMENT_ID);
        }
        assertThat(analytics.of(SMALL_DEPARTMENT_ID, 2022, 2026).topPerformers().status())
            .isEqualTo(baseline + 2 >= MIN_GROUP_SIZE ? TopPerformers.Status.LISTED
                : TopPerformers.Status.GROUP_TOO_SMALL);
        user("it.units.fifth", SMALL_DEPARTMENT_ID);
        Objects.requireNonNull(caches.getCache(UnitAggregates.CACHE)).clear();

        TopPerformers top = analytics.of(SMALL_DEPARTMENT_ID, 2022, 2026).topPerformers();

        assertThat(top.status()).isEqualTo(TopPerformers.Status.LISTED);
        assertThat(top.people()).extracting(TopPerformers.Person::userId).contains(named).doesNotContain(silent);
    }

    @Test
    void ac1_9_cachedAggregatesKeepTheirTimeWhileConsentIsReadAtEveryRequest(CapturedOutput output) {
        for (int index = 0; index < MIN_GROUP_SIZE; index++) {
            user("it.units.group" + index, SMALL_DEPARTMENT_ID);
        }
        long named = user("it.units.named", SMALL_DEPARTMENT_ID);
        grantAnalytics(named);
        award(named, SMALL_DEPARTMENT_ID, false, "APPROVED", LocalDate.of(2025, 3, 1), MINISTRY_NAME, false);
        UnitAnalytics first = analytics.of(SMALL_DEPARTMENT_ID, 2022, 2026);
        award(named, SMALL_DEPARTMENT_ID, false, "APPROVED", LocalDate.of(2025, 4, 1), MINISTRY_NAME, false);
        jdbc.update("insert into consent_records (user_id, consent_type, consent_version, is_granted, withdrawn_at) "
            + "values (?, 'ANALYTICS', '1.0', false, now())", named);

        UnitAnalytics second = analytics.of(SMALL_DEPARTMENT_ID, 2022, 2026);

        assertThat(first.topPerformers().people()).extracting(TopPerformers.Person::userId).contains(named);
        assertThat(second.generatedAt()).isEqualTo(first.generatedAt());
        assertThat(second.totals().approved()).isEqualTo(first.totals().approved());
        assertThat(second.topPerformers().people()).extracting(TopPerformers.Person::userId).doesNotContain(named);
        assertThat(redis.keys("*unit-analytics*")).isNotEmpty();
        redis.opsForValue().set("awardapp:unit-analytics::" + SMALL_DEPARTMENT_ID + ":2025:2026", "not json");

        UnitAnalytics recovered = analytics.of(SMALL_DEPARTMENT_ID, 2025, 2026);

        assertThat(recovered.totals().approved().current()).isEqualTo(2);
        assertThat(output).contains("Cache unit-analytics could not be read");
        assertThat(ranking.of(aggregates.of(SMALL_DEPARTMENT_ID, recovered.previousPeriod(), recovered.period()),
            recovered.totals().members()).people()).extracting(TopPerformers.Person::userId).doesNotContain(named);
    }

    private long user(String name, long organizationId) {
        return Objects.requireNonNull(jdbc.queryForObject(INSERT_USER, Long.class, name + "@chnu.edu.ua", "Ім'я",
            name, "ACTIVE", organizationId));
    }

    private void award(long userId, long organizationId, boolean unitAward, String status, LocalDate date,
                       String source, boolean verified) {
        jdbc.update(INSERT_AWARD, userId, organizationId, unitAward ? organizationId : null, status,
            unitAward ? FACULTY_TEACHING : MINISTRY, source, date, verified);
    }

    private void grantAnalytics(long userId) {
        jdbc.update("insert into consent_records (user_id, consent_type, consent_version, is_granted, granted_at) "
            + "values (?, 'ANALYTICS', '1.0', true, now())", userId);
    }

    private long activeMembers(long unitId) {
        return Objects.requireNonNull(namedJdbc.queryForObject("select count(*) from users where account_status = "
            + "'ACTIVE' and organization_id in (:ids)", Map.of("ids", tree.subtree(unitId)), Long.class));
    }
}
