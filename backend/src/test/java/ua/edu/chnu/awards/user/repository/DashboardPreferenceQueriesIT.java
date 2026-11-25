package ua.edu.chnu.awards.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.dto.DashboardLayout;
import ua.edu.chnu.awards.user.dto.DashboardPreferences;

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
    void ac1_6_theTableHasTheSchemeAndLayoutColumnsTheCascadeAndNoAuditTrigger() {
        List<Map<String, Object>> columns = jdbc.queryForList("select column_name, data_type, is_nullable, "
            + "column_default from information_schema.columns where table_name = 'dashboard_preferences' "
            + "order by ordinal_position");
        String foreignKey = jdbc.queryForObject("select pg_get_constraintdef(oid) from pg_constraint "
            + "where conname = 'fk_dashboard_preferences_user'", String.class);
        List<String> triggers = jdbc.queryForList("select tgname from pg_trigger"
            + " where tgrelid = 'dashboard_preferences'::regclass and not tgisinternal", String.class);

        assertThat(columns).extracting(column -> column.get("column_name"))
            .containsExactly("user_id", "updated_at", "color_scheme", "layouts", "onboarding");
        assertThat(columns.get(2)).containsEntry("is_nullable", "NO");
        assertThat((String) columns.get(2).get("column_default")).startsWith("'SYSTEM'");
        assertThat(columns.get(3)).containsEntry("data_type", "jsonb").containsEntry("is_nullable", "NO");
        assertThat((String) columns.get(3).get("column_default")).startsWith("'{}'");
        assertThat(foreignKey).contains("REFERENCES users(user_id) ON DELETE CASCADE");
        assertThat(triggers).isEmpty();
    }

    @Test
    void ac1_3_aChoiceIsInsertedThenReplacedByTheLastWrite() {
        assertThat(queries.find(personId)).isEmpty();
        DashboardPreferences last = new DashboardPreferences("LIGHT",
            Map.of("HALL_OF_FAME", new DashboardLayout(List.of("TOP", "KPI"), List.of("FACT"))));

        queries.save(personId, new DashboardPreferences("DARK",
            Map.of("MY_AWARDS", new DashboardLayout(List.of(), List.of("RANK")))));
        queries.save(personId, last);

        assertThat(queries.find(personId)).contains(last);
        assertThat(jdbc.queryForObject("select count(*) from dashboard_preferences where user_id = ?", Integer.class,
            personId)).isOne();
        assertThat(jdbc.queryForObject("select layouts #>> '{HALL_OF_FAME,order,0}' from dashboard_preferences "
            + "where user_id = ?", String.class, personId)).isEqualTo("TOP");
    }

    @Test
    void ac1_3_defaultLayoutsAreStoredAsAnEmptyObject() {
        queries.save(personId, new DashboardPreferences("SYSTEM", Map.of()));

        assertThat(queries.find(personId)).contains(new DashboardPreferences("SYSTEM", Map.of()));
        assertThat(jdbc.queryForObject("select layouts::text from dashboard_preferences where user_id = ?",
            String.class, personId)).isEqualTo("{}");
    }

    @Test
    void ac1_4_theDatabaseRejectsAnUnknownScheme() {
        assertThatThrownBy(() -> jdbc.update("insert into dashboard_preferences (user_id, color_scheme) "
            + "values (?, 'BLUE')", personId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ac1_7_theChoiceIsDeleted() {
        queries.save(personId, new DashboardPreferences("DARK", Map.of()));

        assertThat(queries.delete(personId)).isOne();
        assertThat(queries.delete(personId)).isZero();
        assertThat(queries.find(personId)).isEmpty();
    }
}
