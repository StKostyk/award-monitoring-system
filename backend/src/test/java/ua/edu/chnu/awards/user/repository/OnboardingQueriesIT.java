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
import ua.edu.chnu.awards.support.ConsentRows;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.dto.DashboardPreferences;
import ua.edu.chnu.awards.user.dto.OnboardingFacts;
import ua.edu.chnu.awards.user.dto.OnboardingRequest;

class OnboardingQueriesIT extends AbstractIntegrationTest {

    @Autowired
    private OnboardingQueries queries;

    @Autowired
    private DashboardPreferenceQueries preferences;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long personId;

    @BeforeEach
    void createPerson() {
        personId = userRepository.save(TestUsers.user("it.onboarding." + UUID.randomUUID() + "@chnu.edu.ua",
            organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow())).getId();
    }

    @AfterEach
    void deletePerson() {
        ConsentRows.purge(jdbc, personId);
        userRepository.deleteById(personId);
    }

    @Test
    void ac9_3_theColumnIsJsonbNotNullWithAnEmptyDefault() {
        Map<String, Object> column = jdbc.queryForMap("select data_type, is_nullable, column_default"
            + " from information_schema.columns where table_name = 'dashboard_preferences'"
            + " and column_name = 'onboarding'");

        assertThat(column).containsEntry("data_type", "jsonb").containsEntry("is_nullable", "NO");
        assertThat(column.get("column_default").toString()).startsWith("'{}'");
    }

    @Test
    void ac9_3_progressIsInsertedWithTheDefaultChoiceAndReadBack() {
        assertThat(queries.find(personId)).isEmpty();

        queries.save(personId, new OnboardingRequest(List.of("PROFILE"), true, false));

        assertThat(queries.find(personId)).contains(new OnboardingRequest(List.of("PROFILE"), true, false));
        assertThat(preferences.find(personId)).contains(new DashboardPreferences("SYSTEM", Map.of()));
    }

    @Test
    void ac9_3_theDashboardChoiceAndTheProgressDoNotOverwriteEachOther() {
        queries.save(personId, new OnboardingRequest(List.of("NOTIFICATIONS"), false, true));

        preferences.save(personId, new DashboardPreferences("DARK", Map.of()));
        queries.save(personId, new OnboardingRequest(List.of("PROFILE"), false, true));

        assertThat(preferences.find(personId).orElseThrow().colorScheme()).isEqualTo("DARK");
        assertThat(queries.find(personId)).contains(new OnboardingRequest(List.of("PROFILE"), false, true));
    }

    @Test
    void ac9_2_withoutAcknowledgementAwardOrDelegationNoFactHolds() {
        assertThat(queries.facts(personId)).isEqualTo(new OnboardingFacts(false, false, false));
    }

    @Test
    void ac9_2_anAcknowledgedNoticeIsAFact() {
        jdbc.update("insert into consent_records (user_id, consent_type, consent_version, is_granted, granted_at)"
            + " values (?, 'DATA_PROCESSING', '1.0', true, now())", personId);

        assertThat(queries.facts(personId).noticeAcknowledged()).isTrue();
    }
}
