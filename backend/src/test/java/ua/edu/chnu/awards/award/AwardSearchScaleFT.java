package ua.edu.chnu.awards.award;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import ua.edu.chnu.awards.authz.RoleType;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractFunctionalTest;
import ua.edu.chnu.awards.support.AwardApi;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.repository.UserRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AwardSearchScaleFT extends AbstractFunctionalTest {

    private static final String OWNER = "ft.search.scale@chnu.edu.ua";
    private static final String READER = "ft.search.reader@chnu.edu.ua";
    private static final int AWARDS = 100_000;
    private static final Duration LIMIT = Duration.ofSeconds(1);
    private static final String TWO_WORDS = "премія конференції";
    private static final String WORDS = "array['премія','грамота','диплом','медаль','стипендія','подяка','грант',"
        + "'відзнака','нагорода','сертифікат']";
    private static final String EVENTS = "array['конференції','олімпіади','конкурсу','фестивалю','форуму','семінару',"
        + "'турніру','виставки','школи','хакатону','симпозіуму','чемпіонату']";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long ownerId;

    @BeforeAll
    void createAwards() {
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        ownerId = withRole(OWNER, department, RoleType.EMPLOYEE, department);
        withRole(READER, department, RoleType.EMPLOYEE, department);
        jdbc.execute("alter table awards disable trigger user");
        try {
            jdbc.update("insert into awards (user_id, organization_id, category_id, title, title_uk, description_uk,"
                + " awarding_organization, award_date, status, visibility)"
                + " select ?, ?, 13, 'Award ' || i, (" + WORDS + ")[1 + i % 10] || ' ' || (" + EVENTS
                + ")[1 + i % 12] || ' ' || md5(i::text), 'Опис ' || md5((i * 7)::text),"
                + " 'Організатор ' || (i % 500), date '2020-01-01' + (i % 1500), 'APPROVED', 'UNIVERSITY'"
                + " from generate_series(1, ?) as i", ownerId, TestUsers.DAI_DEPARTMENT_ID, AWARDS);
        } finally {
            jdbc.execute("alter table awards enable trigger user");
        }
        jdbc.execute("analyze awards");
    }

    @AfterAll
    void deleteAwards() {
        jdbc.execute("alter table awards disable trigger user");
        try {
            jdbc.update("delete from awards where user_id = ?", ownerId);
        } finally {
            jdbc.execute("alter table awards enable trigger user");
        }
        List.of(OWNER, READER).forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @Test
    void ac1_8_aTwoWordSearchOverAHundredThousandAwardsAnswersWithinOneSecond() {
        Duration achievements = timed(tokenOf(READER), "/api/v1/achievements");
        Duration own = timed(tokenOf(OWNER), AwardApi.AWARDS);

        assertThat(achievements).isLessThan(LIMIT);
        assertThat(own).isLessThan(LIMIT);
    }

    @Test
    void ac1_8_theSearchUsesTheGinIndexes() {
        assertThat(plan("search_vector @@ to_tsquery('simple', 'прем:* & конференц:*')")).contains("idx_awards_search");
        assertThat(plan("search_text %> 'horizn'")).contains("trgm_awards_search");
    }

    private String plan(String condition) {
        return String.join("\n", jdbc.queryForList("explain select award_id from awards where status = 'APPROVED'"
            + " and " + condition, String.class));
    }

    private static Duration timed(String token, String path) {
        as(token).queryParam("q", TWO_WORDS).get(path).then().statusCode(HttpStatus.OK.value());
        long start = System.nanoTime();
        as(token).queryParam("q", TWO_WORDS).get(path).then().statusCode(HttpStatus.OK.value());
        return Duration.ofNanos(System.nanoTime() - start);
    }
}
