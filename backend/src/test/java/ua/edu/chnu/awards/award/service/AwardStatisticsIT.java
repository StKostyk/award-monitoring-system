package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import ua.edu.chnu.awards.analytics.dto.PersonalAnalytics;
import ua.edu.chnu.awards.analytics.dto.RankStatus;
import ua.edu.chnu.awards.analytics.service.PersonalAnalyticsService;
import ua.edu.chnu.awards.award.dto.CategoryYearCount;
import ua.edu.chnu.awards.award.dto.CountedTotals;
import ua.edu.chnu.awards.award.dto.DepartmentStanding;
import ua.edu.chnu.awards.award.dto.OpenAwards;
import ua.edu.chnu.awards.award.dto.TimelineEntry;
import ua.edu.chnu.awards.award.dto.YearRange;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.AwardRows;
import ua.edu.chnu.awards.support.ConsentRows;

class AwardStatisticsIT extends AbstractIntegrationTest {

    private static final long DEPARTMENT_ID = 68L;
    private static final long LOAD_DEPARTMENT_ID = 67L;
    private static final long STATE_PRIZE = 12L;
    private static final long FACULTY_TEACHING = 31L;
    private static final int LOAD_ACCOUNTS = 200;
    private static final int LOAD_AWARDS_EACH = 50;
    private static final long MAX_RESPONSE_MILLIS = 1000;
    private static final YearRange PERIOD = new YearRange(2021, 2026);
    private static final String INSERT_USER = "insert into users (email_address, first_name, last_name, "
        + "password_hash, account_status, organization_id) values (?, 'Test', 'User', 'x', ?, ?) returning user_id";

    @Autowired
    private AwardStatistics statistics;

    @Autowired
    private PersonalAnalyticsService analytics;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;

    private long ownerId;
    private long otherId;

    @BeforeEach
    void setUp() {
        ownerId = user("it.stats.owner@chnu.edu.ua", "ACTIVE", DEPARTMENT_ID);
        otherId = user("it.stats.other@chnu.edu.ua", "ACTIVE", DEPARTMENT_ID);
    }

    @AfterEach
    void tearDown() {
        jdbc.query("select user_id from users where email_address like 'it.stats.%'",
            (RowCallbackHandler) row -> ConsentRows.purge(jdbc, row.getLong(1)));
        jdbc.update("delete from awards where user_id in (select user_id from users where email_address like "
            + "'it.stats.%')");
        jdbc.update("delete from users where email_address like 'it.stats.%'");
    }

    @Test
    void ac1_2_onlyApprovedPersonalAwardsInThePeriodCount() {
        long verifiedId = approved(ownerId, LocalDate.of(2021, 1, 1));
        jdbc.update("update awards set verification_badge = true where award_id = ?", verifiedId);
        approved(ownerId, LocalDate.of(2023, 6, 1));
        approved(ownerId, LocalDate.of(2026, 5, 2));
        approved(ownerId, LocalDate.of(2020, 12, 31));
        approved(otherId, LocalDate.of(2023, 6, 1));
        AwardRows.award(jdbc, ownerId).status("APPROVED").unit(DEPARTMENT_ID).awardDate(LocalDate.of(2023, 1, 1))
            .insert();
        AwardRows.award(jdbc, ownerId).status("PENDING").awardDate(LocalDate.of(2023, 1, 1)).insert();
        AwardRows.award(jdbc, ownerId).status("REJECTED").awardDate(LocalDate.of(2023, 1, 1)).insert();
        AwardRows.award(jdbc, ownerId).awardDate(LocalDate.of(1990, 1, 1)).insert();
        AwardRows.award(jdbc, ownerId).insert();
        AwardRows.award(jdbc, ownerId).unit(DEPARTMENT_ID).insert();

        assertThat(statistics.totals(ownerId, PERIOD)).isEqualTo(new CountedTotals(3, 1, LocalDate.of(2021, 1, 1),
            LocalDate.of(2026, 5, 2)));
        assertThat(statistics.firstYear(ownerId)).hasValue(2020);
        assertThat(statistics.openAwards(ownerId)).isEqualTo(new OpenAwards(2, 1));
        assertThat(statistics.firstYear(user("it.stats.none@chnu.edu.ua", "ACTIVE", DEPARTMENT_ID))).isEmpty();
    }

    @Test
    void ac1_3_countsAreGroupedByYearAndCategory() {
        approved(ownerId, LocalDate.of(2023, 2, 1));
        approved(ownerId, LocalDate.of(2023, 3, 1));
        AwardRows.award(jdbc, ownerId).status("APPROVED").category(FACULTY_TEACHING)
            .awardDate(LocalDate.of(2023, 4, 1)).insert();
        AwardRows.award(jdbc, ownerId).status("APPROVED").category(STATE_PRIZE)
            .awardDate(LocalDate.of(2021, 4, 1)).insert();

        List<CategoryYearCount> counts = statistics.byCategoryAndYear(ownerId, PERIOD);

        assertThat(counts).extracting(CategoryYearCount::year, count -> count.category().id(),
                CategoryYearCount::count)
            .containsExactly(tuple(2021, STATE_PRIZE, 1L),
                tuple(2023, 13L, 2L),
                tuple(2023, FACULTY_TEACHING, 1L));
        assertThat(counts.get(2).category().nameUk()).isEqualTo("Факультетська педагогічна нагорода");
    }

    @Test
    void ac1_4_theTimelineIsNewestFirstAndLimited() {
        long older = approved(ownerId, LocalDate.of(2022, 1, 1));
        long sameDayFirst = approved(ownerId, LocalDate.of(2024, 1, 1));
        long sameDaySecond = approved(ownerId, LocalDate.of(2024, 1, 1));
        jdbc.update("update awards set title_uk = 'Подяка', verification_badge = true where award_id = ?", older);

        List<TimelineEntry> timeline = statistics.timeline(ownerId, PERIOD, 10);

        assertThat(timeline).extracting(TimelineEntry::awardId).containsExactly(sameDaySecond, sameDayFirst, older);
        assertThat(timeline.get(2)).isEqualTo(new TimelineEntry(older, "Letter", "Подяка", LocalDate.of(2022, 1, 1),
            timeline.get(2).category(), "МОН", true));
        assertThat(timeline.get(2).category().id()).isEqualTo(13L);
        assertThat(statistics.timeline(ownerId, PERIOD, 2)).hasSize(2);
    }

    @Test
    void ac1_6_activeMembersWithoutAwardsCountAndInactiveOnesDoNot() {
        approved(ownerId, LocalDate.of(2024, 1, 1));
        approved(otherId, LocalDate.of(2024, 1, 1));
        long ahead = user("it.stats.ahead@chnu.edu.ua", "ACTIVE", DEPARTMENT_ID);
        approved(ahead, LocalDate.of(2024, 1, 1));
        approved(ahead, LocalDate.of(2025, 1, 1));
        approved(ahead, LocalDate.of(2019, 1, 1));
        user("it.stats.empty@chnu.edu.ua", "ACTIVE", DEPARTMENT_ID);
        long suspended = user("it.stats.suspended@chnu.edu.ua", "SUSPENDED", DEPARTMENT_ID);
        approved(suspended, LocalDate.of(2024, 1, 1));
        user("it.stats.retired@chnu.edu.ua", "RETIRED", DEPARTMENT_ID);
        user("it.stats.pending@chnu.edu.ua", "PENDING", DEPARTMENT_ID);

        assertThat(statistics.standing(DEPARTMENT_ID, ownerId, 1, PERIOD))
            .isEqualTo(new DepartmentStanding(4, 1, 1));
        assertThat(statistics.standing(DEPARTMENT_ID, ownerId, 0, new YearRange(2019, 2019)))
            .isEqualTo(new DepartmentStanding(4, 1, 2));
    }

    @Test
    void ac1_8_tenThousandAwardsOfTwoHundredAccountsAnswerWithinASecond() {
        jdbc.update("insert into users (email_address, first_name, last_name, password_hash, account_status, "
            + "organization_id) select 'it.stats.load' || n || '@chnu.edu.ua', 'Test', 'User', 'x', 'ACTIVE', ? "
            + "from generate_series(1, ?) n", LOAD_DEPARTMENT_ID, LOAD_ACCOUNTS);
        jdbc.update("insert into awards (user_id, organization_id, title, status, category_id, awarding_organization, "
            + "award_date) select u.user_id, u.organization_id, 'Letter ' || n, 'APPROVED', 13, 'МОН', "
            + "make_date(2015 + n % 11, 1 + n % 12, 1) from users u cross join generate_series(1, ?) n "
            + "where u.email_address like 'it.stats.load%'", LOAD_AWARDS_EACH);
        jdbc.execute("analyze awards");
        jdbc.execute("analyze users");
        long target = jdbc.queryForObject("select user_id from users where email_address = "
            + "'it.stats.load1@chnu.edu.ua'", Long.class);
        jdbc.update("insert into consent_records (user_id, consent_type, consent_version, is_granted, granted_at) "
            + "values (?, 'ANALYTICS', '1.0', true, now())", target);
        analytics.of(target, null, null);

        long started = System.nanoTime();
        PersonalAnalytics result = analytics.of(target, null, null);
        long millis = (System.nanoTime() - started) / 1_000_000;

        assertThat(millis).isLessThan(MAX_RESPONSE_MILLIS);
        assertThat(result.totals().approved()).isEqualTo(LOAD_AWARDS_EACH);
        assertThat(result.rank().status()).isEqualTo(RankStatus.RANKED);
        assertThat(result.rank().groupSize()).isEqualTo(LOAD_ACCOUNTS);
        assertThat(result.rank().position()).isEqualTo(1);
        assertThat(result.rank().sharedWith()).isEqualTo(LOAD_ACCOUNTS - 1);
        String plan = String.join("\n", namedJdbc.queryForList("explain " + AwardStatistics.STANDING,
            Map.of("userId", target, "from", 2015, "to", 2026, "departmentId", LOAD_DEPARTMENT_ID,
                "count", LOAD_AWARDS_EACH), String.class));
        assertThat(plan).containsPattern("Index Scan using idx_awards_user\\w* on awards")
            .doesNotContain("Seq Scan on awards");
    }

    private long user(String email, String status, long organizationId) {
        return jdbc.queryForObject(INSERT_USER, Long.class, email, status, organizationId);
    }

    private long approved(long userId, LocalDate date) {
        return AwardRows.award(jdbc, userId).status("APPROVED").awardDate(date).insert();
    }
}
