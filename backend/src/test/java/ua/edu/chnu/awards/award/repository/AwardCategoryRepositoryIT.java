package ua.edu.chnu.awards.award.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import ua.edu.chnu.awards.award.entity.AwardCategory;
import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.support.AbstractJpaSliceTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;

class AwardCategoryRepositoryIT extends AbstractJpaSliceTest {

    private static final String SEED = "db/migration/R__seed_award_categories.sql";

    @Autowired
    private AwardCategoryRepository repository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void ac01_ac03_everyLevelHasARootWithAtLeastTwoChildren() {
        List<AwardCategory> active = repository.findByActiveTrueOrderBySortOrderAscNameAsc();
        Map<RecognitionLevel, List<AwardCategory>> roots = active.stream()
            .filter(category -> category.getParent() == null)
            .collect(Collectors.groupingBy(AwardCategory::getLevel));

        assertThat(roots.keySet()).containsExactlyInAnyOrder(RecognitionLevel.values());
        roots.values().forEach(levelRoots -> assertThat(levelRoots).hasSize(1));
        roots.values().stream().map(levelRoots -> levelRoots.get(0)).forEach(root -> {
            assertThat(active).filteredOn(category -> category.getParent() != null
                && category.getParent().getId().equals(root.getId())).hasSizeGreaterThanOrEqualTo(2);
            assertThat(root.getNameUk()).isNotBlank();
        });
    }

    @Test
    void ac02_inactiveCategoriesAreNotListed() {
        jdbc.update("update award_categories set is_active = false where category_id = 44");

        assertThat(repository.findByActiveTrueOrderBySortOrderAscNameAsc())
            .extracting(AwardCategory::getId).doesNotContain(44L).contains(43L);
    }

    @Test
    void ac04_runningTheSeedAgainKeepsAwardsAndReactivatesSystemCategories() {
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        User owner = userRepository.saveAndFlush(TestUsers.user("seed.owner@chnu.edu.ua", department));
        jdbc.update("insert into awards (user_id, category_id, title, awarding_organization, award_date) "
            + "values (?, 13, 'Ministry letter of thanks', 'Ministry of Education', date '2025-05-01')",
            owner.getId());
        jdbc.update("update award_categories set name_uk = 'змінено', is_active = false where category_id = 13");
        jdbc.update("insert into award_categories (category_id, name, level, is_system) "
            + "values (99, 'Retired system category', 'LOCAL', true)");

        runSeed();

        assertThat(jdbc.queryForObject("select count(*) from awards where user_id = ?", Integer.class,
            owner.getId())).isEqualTo(1);
        assertThat(jdbc.queryForMap("select name_uk, is_active from award_categories where category_id = 13"))
            .containsEntry("name_uk", "Відзнака міністерства").containsEntry("is_active", true);
        assertThat(jdbc.queryForObject("select is_active from award_categories where category_id = 99",
            Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("select nextval('award_categories_category_id_seq')", Long.class))
            .isGreaterThan(99L);
    }

    @Test
    void ac04_namesMovingBetweenSystemCategoriesDoNotBreakTheSeed() {
        jdbc.update("update award_categories set name = 'State Prize (old)' where category_id = 12");
        jdbc.update("update award_categories set name = 'State Prize' where category_id = 11");
        jdbc.update("update award_categories set name = 'Former local root' where category_id = 70");
        jdbc.update("insert into award_categories (category_id, name, level, is_system) "
            + "values (98, 'Local Awards', 'LOCAL', true)");

        runSeed();

        assertThat(jdbc.queryForObject("select name from award_categories where category_id = 11", String.class))
            .isEqualTo("National Science Award");
        assertThat(jdbc.queryForObject("select name from award_categories where category_id = 12", String.class))
            .isEqualTo("State Prize");
        assertThat(jdbc.queryForObject("select name from award_categories where category_id = 70", String.class))
            .isEqualTo("Local Awards");
        assertThat(jdbc.queryForMap("select name, is_active from award_categories where category_id = 98"))
            .containsEntry("name", "Local Awards (retired 98)").containsEntry("is_active", false);
    }

    @Test
    void ac04_aCategoryCreatedByUsersOnASeededIdStopsTheSeed() {
        jdbc.update("update award_categories set is_system = false, name = 'Our speciality' where category_id = 50");

        assertThatThrownBy(this::runSeed).rootCause()
            .hasMessageContaining("category_id is held by a category that is not");
    }

    @Test
    void ac04_aCategoryCreatedByUsersWithASeededNameStopsTheSeed() {
        jdbc.update("update award_categories set name = 'Former local root' where category_id = 70");
        jdbc.update("insert into award_categories (category_id, name, level) values (97, 'Local Awards', 'LOCAL')");

        assertThatThrownBy(this::runSeed).rootCause().hasMessageContaining("name is held by a category that is not");
    }

    private void runSeed() {
        ScriptUtils.executeSqlScript(DataSourceUtils.getConnection(dataSource),
            new EncodedResource(new ClassPathResource(SEED), StandardCharsets.UTF_8), false, false,
            ScriptUtils.DEFAULT_COMMENT_PREFIX, ScriptUtils.EOF_STATEMENT_SEPARATOR,
            ScriptUtils.DEFAULT_BLOCK_COMMENT_START_DELIMITER, ScriptUtils.DEFAULT_BLOCK_COMMENT_END_DELIMITER);
    }
}
