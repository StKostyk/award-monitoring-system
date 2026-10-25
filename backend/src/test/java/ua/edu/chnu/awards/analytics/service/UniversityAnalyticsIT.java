package ua.edu.chnu.awards.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Statement;
import java.time.LocalDate;
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
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import ua.edu.chnu.awards.analytics.dto.Figure;
import ua.edu.chnu.awards.analytics.dto.UnitComparisonRow;
import ua.edu.chnu.awards.analytics.dto.UniversityAnalytics;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;

@ExtendWith(OutputCaptureExtension.class)
class UniversityAnalyticsIT extends AbstractIntegrationTest {

    private static final long UNIVERSITY_ID = 1L;
    private static final long COLLEGE_ID = 14L;
    private static final long FACULTY_ID = 2L;
    private static final long DEPARTMENT_ID = 20L;
    private static final long MINISTRY = 13L;
    private static final long FACULTY_TEACHING = 31L;
    private static final String CACHE_KEY = "awardapp:unit-analytics::" + UNIVERSITY_ID + ":2022:2026";
    private static final String INSERT_USER = "insert into users (email_address, first_name, last_name, "
        + "password_hash, account_status, organization_id) values (?, 'Ім''я', 'IT', 'x', 'ACTIVE', ?) "
        + "returning user_id";
    private static final String INSERT_AWARD = "insert into awards (user_id, organization_id, recipient_org_id, "
        + "title, status, category_id, awarding_organization, award_date) values (?, ?, ?, 'IT university award', ?, "
        + "?, ?, ?)";

    @Autowired
    private UniversityAnalyticsService analytics;

    @Autowired
    private CacheManager caches;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearCache() {
        Objects.requireNonNull(caches.getCache(UnitAggregates.CACHE)).clear();
    }

    @AfterEach
    void tearDown() {
        withoutTriggers("delete from awards where user_id in (select user_id from users where email_address like "
            + "'it.university.%')");
        jdbc.update("delete from users where email_address like 'it.university.%'");
        Objects.requireNonNull(caches.getCache(UnitAggregates.CACHE)).clear();
    }

    @Test
    void ac1_3_ac1_4_countedAwardsOfTheWholeUniversityLandInTheirFacultyCollegeOrTheUniversityLevel() {
        final UniversityAnalytics before = analytics.of(2022, 2026);
        long member = user("it.university.member", DEPARTMENT_ID);
        final long student = user("it.university.college", COLLEGE_ID);
        final long rector = user("it.university.rector", UNIVERSITY_ID);
        award(member, DEPARTMENT_ID, false, "APPROVED", LocalDate.of(2024, 3, 1));
        award(member, DEPARTMENT_ID, false, "PENDING", LocalDate.of(2024, 4, 1));
        award(member, DEPARTMENT_ID, false, "DRAFT", LocalDate.of(2024, 5, 1));
        award(member, DEPARTMENT_ID, false, "REJECTED", LocalDate.of(2024, 6, 1));
        award(member, DEPARTMENT_ID, false, "APPROVED", LocalDate.of(2019, 6, 1));
        award(student, COLLEGE_ID, false, "APPROVED", LocalDate.of(2025, 2, 1));
        award(rector, UNIVERSITY_ID, true, "APPROVED", LocalDate.of(2026, 1, 15));
        Objects.requireNonNull(caches.getCache(UnitAggregates.CACHE)).clear();

        UniversityAnalytics after = analytics.of(2022, 2026);

        assertThat(after.unit().id()).isEqualTo(UNIVERSITY_ID);
        assertThat(after.totals().approved().current() - before.totals().approved().current()).isEqualTo(3);
        assertThat(after.totals().approved().previous() - before.totals().approved().previous()).isEqualTo(1);
        assertThat(current(after, FACULTY_ID) - current(before, FACULTY_ID)).isEqualTo(1);
        assertThat(current(after, COLLEGE_ID) - current(before, COLLEGE_ID)).isEqualTo(1);
        assertThat(current(after, UNIVERSITY_ID) - current(before, UNIVERSITY_ID)).isEqualTo(1);
        assertThat(after.units().getLast().facultyLevel()).isTrue();
        assertThat(after.units().getLast().unit().id()).isEqualTo(UNIVERSITY_ID);
        assertThat(after.units()).filteredOn(row -> !row.facultyLevel())
            .allSatisfy(row -> assertThat(row.unit().type().name()).isIn("FACULTY", "COLLEGE"));
        assertThat(after.units().stream().mapToLong(row -> row.approved().current()).sum())
            .isEqualTo(after.totals().approved().current());
    }

    @Test
    void ac1_8_theUniversitySharesTheUnitCacheWithThePreviousSourcesAndAnOldEntryIsAMiss(CapturedOutput output) {
        long member = user("it.university.cached", DEPARTMENT_ID);
        award(member, DEPARTMENT_ID, false, "APPROVED", LocalDate.of(2024, 3, 1));
        UniversityAnalytics first = analytics.of(2022, 2026);
        award(member, DEPARTMENT_ID, false, "APPROVED", LocalDate.of(2024, 4, 1));

        UniversityAnalytics second = analytics.of(2022, 2026);

        assertThat(second.generatedAt()).isEqualTo(first.generatedAt());
        assertThat(second.totals().approved()).isEqualTo(first.totals().approved());
        String entry = redis.opsForValue().get(CACHE_KEY);
        assertThat(entry).contains("\"previousSources\"");
        redis.opsForValue().set(CACHE_KEY, entry.replaceFirst("\"previousSources\":\\[[^\\]]*\\],", ""));

        UniversityAnalytics recovered = analytics.of(2022, 2026);

        assertThat(output).contains("Cache unit-analytics could not be read");
        assertThat(recovered.totals().approved().current()).isEqualTo(first.totals().approved().current() + 1);
    }

    private static long current(UniversityAnalytics analytics, long unitId) {
        return analytics.units().stream().filter(row -> row.unit().id() == unitId).findFirst()
            .map(UnitComparisonRow::approved).map(Figure::current).orElse(0L);
    }

    private long user(String name, long organizationId) {
        return Objects.requireNonNull(jdbc.queryForObject(INSERT_USER, Long.class, name + "@chnu.edu.ua",
            organizationId));
    }

    private void award(long userId, long organizationId, boolean unitAward, String status, LocalDate date) {
        jdbc.update(INSERT_AWARD, userId, organizationId, unitAward ? organizationId : null, status,
            unitAward ? FACULTY_TEACHING : MINISTRY, "Міністерство освіти і науки України", date);
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
