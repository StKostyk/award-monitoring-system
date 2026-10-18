package ua.edu.chnu.awards.gdpr.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import ua.edu.chnu.awards.common.retention.RetentionContext;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.ConsentRows;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.repository.UserRepository;

/**
 * Fixtures of the retention tests: people created for one test and deleted after it, and the context of a run.
 */
abstract class AbstractRetentionIT extends AbstractIntegrationTest {

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected TransactionTemplate transactions;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    private final List<Long> people = new ArrayList<>();

    @AfterEach
    void deletePeople() {
        people.forEach(id -> {
            jdbc.update("delete from erasure_requests where user_id = ?", id);
            jdbc.update("delete from legal_holds where user_id = ?", id);
            jdbc.update("delete from one_time_tokens where user_id = ?", id);
            ConsentRows.purge(jdbc, id);
        });
        userRepository.deleteAllById(people);
        people.clear();
    }

    /**
     * Creates an active person of the DAI department, deleted after the test.
     *
     * @param prefix the start of the e-mail address
     * @return the account id
     */
    protected long newPerson(String prefix) {
        long id = userRepository.save(TestUsers.user(prefix + UUID.randomUUID() + "@chnu.edu.ua",
            organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow())).getId();
        people.add(id);
        return id;
    }

    /**
     * A run starting now.
     *
     * @param held      the people under a legal hold
     * @param batchSize the most rows one batch changes
     * @return the context
     */
    protected RetentionContext context(Set<Long> held, int batchSize) {
        return new RetentionContext(Instant.now(), held, batchSize, transactions);
    }
}
