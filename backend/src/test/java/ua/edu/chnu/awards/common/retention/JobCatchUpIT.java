package ua.edu.chnu.awards.common.retention;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.ConsentRows;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.repository.UserRepository;

class JobCatchUpIT extends AbstractIntegrationTest {

    @Autowired
    private JobCatchUp catchUp;

    @Autowired
    private List<CatchUpJob> jobs;

    @Autowired
    private JobsProperties properties;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long personId;
    private Instant started;

    @BeforeEach
    void createDueErasure() {
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        personId = userRepository.save(TestUsers.user("it.catchup." + UUID.randomUUID() + "@chnu.edu.ua",
            department)).getId();
        jdbc.update("insert into erasure_requests (user_id, requested_at, scheduled_for) "
            + "values (?, now() - interval '31 days', now() - interval '1 hour')", personId);
        started = Instant.now();
    }

    @AfterEach
    void deletePerson() {
        jdbc.update("delete from erasure_requests where user_id = ?", personId);
        ConsentRows.purge(jdbc, personId);
        userRepository.deleteById(personId);
    }

    @Test
    void ac2_6_theCatchUpIsOffOutsideProductionAndCoversTheThreeNightlyJobs() {
        assertThat(properties.catchUpOnStart()).isFalse();
        assertThat(jobs.stream().map(CatchUpJob::catchUpName))
            .contains("erasure job", "retention job", "document sweep");
    }

    @Test
    void ac2_6_aCatchUpErasesTheAccountsDueAndRecordsARetentionRun() {
        catchUp.runAll();

        assertThat(jdbc.queryForObject("select status from erasure_requests where user_id = ?", String.class,
            personId)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select account_status from users where user_id = ?", String.class,
            personId)).isEqualTo("DELETED");
        assertThat(jdbc.queryForObject("select count(*) from retention_runs where started_at >= ?", Long.class,
            Timestamp.from(started))).isPositive();
    }
}
