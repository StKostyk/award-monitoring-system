package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import ua.edu.chnu.awards.analytics.dto.UnitAnalytics;
import ua.edu.chnu.awards.analytics.dto.UniversityAnalytics;
import ua.edu.chnu.awards.analytics.service.UnitAggregates;
import ua.edu.chnu.awards.analytics.service.UnitAnalyticsService;
import ua.edu.chnu.awards.analytics.service.UniversityAnalyticsService;
import ua.edu.chnu.awards.authz.TokenClaimConstants;
import ua.edu.chnu.awards.award.dto.YearRange;
import ua.edu.chnu.awards.organization.OrganizationTree;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;

class UnitStatisticsIT extends AbstractIntegrationTest {

    private static final long LOAD_FACULTY_ID = 3L;
    private static final int LOAD_DEPARTMENTS = 10;
    private static final int LOAD_MEMBERS = 600;
    private static final int LOAD_AWARDS_EACH = 50;
    private static final long MAX_RESPONSE_MILLIS = 2000;
    private static final long ELSEWHERE_DEPARTMENT_ID = 20L;
    private static final int ELSEWHERE_AWARDS = 270_000;
    private static final int UNIVERSITY_FACULTIES = 10;
    private static final int UNIVERSITY_MEMBERS = 3_000;
    private static final int UNIVERSITY_AWARDS = 100_000;
    private static final long MAX_UNIVERSITY_MILLIS = 3_000;

    @Autowired
    private UnitStatistics statistics;

    @Autowired
    private UnitAnalyticsService analytics;

    @Autowired
    private UniversityAnalyticsService universityAnalytics;

    @Autowired
    private CacheManager caches;

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
        withoutTriggers("delete from awards where user_id in (select user_id from users where email_address like "
            + "'it.unitstats.%')");
        jdbc.update("delete from users where email_address like 'it.unitstats.%'");
        Objects.requireNonNull(caches.getCache(UnitAggregates.CACHE)).clear();
    }

    @Test
    void ac1_4_anEmptySetOfOrganisationsCountsNothing() {
        YearRange period = new YearRange(2022, 2026);

        assertThat(statistics.counts(Set.of(), period)).isEmpty();
        assertThat(statistics.owners(Set.of(), new YearRange(2017, 2021), period)).isEmpty();
        assertThat(statistics.sources(Set.of(), period)).isEmpty();
        assertThat(statistics.members(Set.of())).isEmpty();
    }

    @Test
    void ac1_10_thirtyThousandAwardsOfSixHundredMembersAnswerWithinTwoSecondsThroughTheIndex() {
        jdbc.update("insert into users (email_address, first_name, last_name, password_hash, account_status, "
            + "organization_id) select 'it.unitstats.load' || n || '@chnu.edu.ua', 'Test', 'User', 'x', 'ACTIVE', "
            + "(select org_id from organizations where parent_org_id = ? and org_type = 'DEPARTMENT' "
            + "order by org_id offset n % ? limit 1) from generate_series(1, ?) n", LOAD_FACULTY_ID,
            LOAD_DEPARTMENTS, LOAD_MEMBERS);
        jdbc.update("insert into awards (user_id, organization_id, title, status, category_id, awarding_organization, "
            + "award_date) select u.user_id, u.organization_id, 'Load ' || n, 'APPROVED', "
            + "case when n % 2 = 0 then 13 else 31 end, 'Source ' || (n % 40), make_date(2015 + n % 12, 1 + n % 9, 1) "
            + "from users u cross join generate_series(1, ?) n where u.email_address like 'it.unitstats.load%'",
            LOAD_AWARDS_EACH);
        jdbc.update("insert into users (email_address, first_name, last_name, password_hash, account_status, "
            + "organization_id) values ('it.unitstats.elsewhere@chnu.edu.ua', 'Test', 'User', 'x', 'ACTIVE', ?)",
            ELSEWHERE_DEPARTMENT_ID);
        withoutTriggers("insert into awards (user_id, organization_id, title, status, category_id, "
            + "awarding_organization, award_date) select u.user_id, u.organization_id, 'Elsewhere ' || n, 'APPROVED', "
            + "13, 'МОН', make_date(2015 + n % 12, 1 + n % 9, 1) from users u cross join generate_series(1, "
            + ELSEWHERE_AWARDS + ") n where u.email_address = 'it.unitstats.elsewhere@chnu.edu.ua'");
        jdbc.execute("analyze awards");
        jdbc.execute("analyze users");

        long started = System.nanoTime();
        UnitAnalytics result = analytics.of(LOAD_FACULTY_ID, null, null);
        long millis = (System.nanoTime() - started) / 1_000_000;

        assertThat(millis).isLessThan(MAX_RESPONSE_MILLIS);
        assertThat(result.units()).filteredOn(row -> !row.facultyLevel()).hasSizeGreaterThanOrEqualTo(LOAD_DEPARTMENTS);
        assertThat(result.totals().members()).isGreaterThanOrEqualTo(LOAD_MEMBERS);
        String plan = String.join("\n", namedJdbc.queryForList("explain " + UnitStatistics.COUNTS,
            Map.of("organizationIds", tree.subtree(LOAD_FACULTY_ID), "from", 2017, "to", 2026), String.class));
        assertThat(plan).contains("idx_awards_organization_status").doesNotContain("Seq Scan on awards");
    }

    @Test
    void ac1_9_aHundredThousandAwardsOfThreeThousandMembersAnswerTheUniversityWithinThreeSeconds() {
        jdbc.update("insert into users (email_address, first_name, last_name, password_hash, account_status, "
            + "organization_id) select 'it.unitstats.university' || n || '@chnu.edu.ua', 'Test', 'User', 'x', "
            + "'ACTIVE', (select org_id from organizations where org_type = 'DEPARTMENT' and parent_org_id in "
            + "(select org_id from organizations where org_type = 'FACULTY' order by org_id limit ?) "
            + "order by org_id offset n % 40 limit 1) from generate_series(1, ?) n", UNIVERSITY_FACULTIES,
            UNIVERSITY_MEMBERS);
        withoutTriggers("insert into awards (user_id, organization_id, title, status, category_id, "
            + "awarding_organization, award_date) select u.user_id, u.organization_id, 'Load ' || n, 'APPROVED', "
            + "case when n % 2 = 0 then 13 else 31 end, 'Source ' || (n % 40), make_date(2015 + n % 12, 1 + n % 9, 1) "
            + "from users u cross join generate_series(1, " + (UNIVERSITY_AWARDS / UNIVERSITY_MEMBERS + 1) + ") n "
            + "where u.email_address like 'it.unitstats.university%'");
        jdbc.execute("analyze awards");
        jdbc.execute("analyze users");

        long started = System.nanoTime();
        UniversityAnalytics result = universityAnalytics.of(null, null);
        long millis = (System.nanoTime() - started) / 1_000_000;

        assertThat(millis).isLessThan(MAX_UNIVERSITY_MILLIS);
        assertThat(result.totals().members()).isGreaterThanOrEqualTo(UNIVERSITY_MEMBERS);
    }

    private void withoutTriggers(String sql) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("set session_replication_role = replica");
                try {
                    statement.execute(sql);
                } finally {
                    statement.execute("set session_replication_role = origin");
                }
            }
            return null;
        });
    }
}
