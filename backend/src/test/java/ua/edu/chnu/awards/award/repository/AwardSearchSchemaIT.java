package ua.edu.chnu.awards.award.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractJpaSliceTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

class AwardSearchSchemaIT extends AbstractJpaSliceTest {

    private static final String MATCHES = "select count(*) from awards where award_id = ?"
        + " and search_vector @@ to_tsquery('simple', ?)";

    @Autowired
    private AwardRepository awards;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private Award prize;

    @BeforeEach
    void setUp() {
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        User owner = userRepository.saveAndFlush(TestUsers.user("search.schema@chnu.edu.ua", department));
        prize = awards.saveAndFlush(Award.builder().owner(owner).organization(department).title("Best Paper Award")
            .titleUk("Премія імені Тараса Шевченка").awardingOrganization("Horizon Europe")
            .descriptionUk("За п’ять років досліджень").build());
    }

    @Test
    void ac1_3_theColumnsHoldTheWeightedWordsWithoutApostrophes() {
        String vector = jdbc.queryForObject("select search_vector::text from awards where award_id = ?",
            String.class, prize.getId());
        String text = jdbc.queryForObject("select search_text from awards where award_id = ?", String.class,
            prize.getId());

        assertThat(vector).contains("'премія':4A", "'шевченка':7A", "'horizon':8B", "'пять':11C")
            .doesNotContain("’");
        assertThat(text).isEqualTo("best paper award премія імені тараса шевченка horizon europe");
        assertThat(jdbc.queryForObject("select word_similarity('horizn', search_text) >= 0.5 from awards"
            + " where award_id = ?", Boolean.class, prize.getId())).isTrue();
    }

    @Test
    void ac1_9_theColumnsFollowAnEditAndAnErasureInTheSameTransaction() {
        assertThat(jdbc.queryForObject(MATCHES, Long.class, prize.getId(), "прем:* & шевченк:*")).isOne();

        prize.setTitleUk("Грамота ректора");
        awards.saveAndFlush(prize);
        assertThat(jdbc.queryForObject(MATCHES, Long.class, prize.getId(), "грамот:*")).isOne();
        assertThat(jdbc.queryForObject(MATCHES, Long.class, prize.getId(), "прем:*")).isZero();

        jdbc.update("update awards set title = null, title_uk = '[erased]', awarding_organization = null,"
            + " description_uk = null where award_id = ?", prize.getId());
        assertThat(jdbc.queryForObject(MATCHES, Long.class, prize.getId(), "horizon:*")).isZero();
        assertThat(jdbc.queryForObject(MATCHES, Long.class, prize.getId(), "пять:*")).isZero();
        assertThat(jdbc.queryForObject("select search_text from awards where award_id = ?", String.class,
            prize.getId())).isEqualTo(" [erased] ");
    }

    @Test
    void ac1_9_theApplicationCannotWriteTheColumns() {
        assertThatThrownBy(() -> jdbc.update("update awards set search_text = 'x' where award_id = ?",
            prize.getId())).isInstanceOf(DataAccessException.class);
    }

    @Test
    void ac1_9_theSearchIndexesReplaceTheUnusedTitleIndexes() {
        assertThat(jdbc.queryForList("select indexname from pg_indexes where tablename = 'awards'", String.class))
            .contains("idx_awards_search", "trgm_awards_search", "trgm_awards_title")
            .doesNotContain("ftidx_awards_title", "ftidx_awards_title_uk");
    }
}
