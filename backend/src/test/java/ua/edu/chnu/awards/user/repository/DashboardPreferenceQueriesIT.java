package ua.edu.chnu.awards.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.TestUsers;

class DashboardPreferenceQueriesIT extends AbstractIntegrationTest {

    @Autowired
    private DashboardPreferenceQueries queries;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long personId;

    @BeforeEach
    void createPerson() {
        personId = userRepository.save(TestUsers.user("it.dashboard." + UUID.randomUUID() + "@chnu.edu.ua",
            organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow())).getId();
    }

    @AfterEach
    void deletePerson() {
        userRepository.deleteById(personId);
    }

    @Test
    void ac2_1_theTableHasTheKeyTheCascadeTheDefaultAndNoAuditTrigger() {
        List<Map<String, Object>> columns = jdbc.queryForList("select column_name, data_type, is_nullable, "
            + "column_default from information_schema.columns where table_name = 'dashboard_preferences' "
            + "order by ordinal_position");
        String foreignKey = jdbc.queryForObject("select pg_get_constraintdef(oid) from pg_constraint "
            + "where conname = 'fk_dashboard_preferences_user'", String.class);
        List<String> triggers = jdbc.queryForList("select tgname from pg_trigger"
            + " where tgrelid = 'dashboard_preferences'::regclass and not tgisinternal", String.class);

        assertThat(columns).extracting(column -> column.get("column_name"))
            .containsExactly("user_id", "hidden_widgets", "updated_at");
        assertThat(columns.get(1)).containsEntry("data_type", "ARRAY").containsEntry("is_nullable", "NO");
        assertThat((String) columns.get(1).get("column_default")).startsWith("'{}'");
        assertThat(columns.get(2)).containsEntry("is_nullable", "NO");
        assertThat(foreignKey).contains("REFERENCES users(user_id) ON DELETE CASCADE");
        assertThat(triggers).isEmpty();
    }

    @Test
    void ac2_2_aChoiceIsInsertedThenReplacedByTheLastWrite() {
        assertThat(queries.find(personId)).isEmpty();

        queries.save(personId, List.of("TIMELINE", "BY_LEVEL"));
        queries.save(personId, List.of("RANK"));

        assertThat(queries.find(personId)).contains(List.of("RANK"));
        assertThat(jdbc.queryForObject("select count(*) from dashboard_preferences where user_id = ?", Integer.class,
            personId)).isOne();
    }

    @Test
    void ac2_2_anEmptyChoiceIsStoredAsAnEmptyArray() {
        queries.save(personId, List.of());

        assertThat(queries.find(personId)).contains(List.of());
        assertThat(jdbc.queryForObject("select cardinality(hidden_widgets) from dashboard_preferences "
            + "where user_id = ?", Integer.class, personId)).isZero();
    }

    @Test
    void ac2_3_theChoiceIsDeleted() {
        queries.save(personId, List.of("SUMMARY"));

        assertThat(queries.delete(personId)).isOne();
        assertThat(queries.delete(personId)).isZero();
        assertThat(queries.find(personId)).isEmpty();
    }
}
