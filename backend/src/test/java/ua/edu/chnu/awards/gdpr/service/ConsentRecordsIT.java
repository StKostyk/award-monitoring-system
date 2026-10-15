package ua.edu.chnu.awards.gdpr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import ua.edu.chnu.awards.gdpr.dto.ConsentOverview;
import ua.edu.chnu.awards.gdpr.dto.ConsentType;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.ConsentRows;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

class ConsentRecordsIT extends AbstractIntegrationTest {

    private static final String OWNER = "it.consents@chnu.edu.ua";
    private static final String INSERT = "insert into consent_records (user_id, consent_type, consent_version,"
        + " is_granted, granted_at, ip_address, user_agent) values (?, ?, '1.0', true, now(), '10.0.0.7', 'Firefox')"
        + " returning consent_id";

    @Autowired
    private ConsentService consentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(TestUsers.user(OWNER,
            organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow()));
    }

    @AfterEach
    void tearDown() {
        ConsentRows.purge(jdbc, owner.getId());
        userRepository.deleteById(owner.getId());
    }

    @Test
    void ac11_onlyTheTwoTypesAreAllowed() {
        assertThat(insert("DATA_PROCESSING")).isPositive();
        assertThat(insert("ANALYTICS")).isPositive();
        assertThatThrownBy(() -> insert("PUBLIC_VISIBILITY")).isInstanceOf(DataAccessException.class)
            .hasMessageContaining("ck_consent_records_type");
    }

    @Test
    void ac11_rowsCannotBeDeletedOrChanged() {
        long id = insert("DATA_PROCESSING");

        assertThatThrownBy(() -> jdbc.update("delete from consent_records where consent_id = ?", id))
            .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("update consent_records set consent_version = '0.9' where consent_id = ?",
            id)).isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("update consent_records set ip_address = null, is_granted = false,"
            + " granted_at = null where consent_id = ?", id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("update consent_records set ip_address = null where consent_id = ?", id))
            .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }

    @Test
    void ac11_theNetworkFactsCanBeCleared() {
        long id = insert("DATA_PROCESSING");

        jdbc.update("update consent_records set ip_address = null, user_agent = null where consent_id = ?", id);

        assertThat(jdbc.queryForObject("select ip_address is null and user_agent is null from consent_records"
            + " where consent_id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    void ac17_concurrentGrantsAppendOneRecord() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Callable<ConsentOverview> grant = () -> {
            start.await();
            return consentService.choose(owner.getId(), ConsentType.ANALYTICS, true);
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<ConsentOverview>> results = List.of(pool.submit(grant), pool.submit(grant));
            start.countDown();
            for (Future<ConsentOverview> result : results) {
                assertThat(result.get().consents().getFirst().granted()).isTrue();
            }
        } finally {
            pool.shutdown();
        }

        assertThat(jdbc.queryForObject("select count(*) from consent_records where user_id = ?", Integer.class,
            owner.getId())).isEqualTo(1);
    }

    private long insert(String type) {
        Long id = jdbc.queryForObject(INSERT, Long.class, owner.getId(), type);
        return id == null ? 0 : id;
    }
}
