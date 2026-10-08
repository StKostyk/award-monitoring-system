package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.jdbc.core.JdbcTemplate;

import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.AwardRows;
import ua.edu.chnu.awards.support.RequestRows;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;

class OverdueNoticesIT extends AbstractIntegrationTest {

    private static final String OWNER = "it.overdue@chnu.edu.ua";
    private static final int INSTANCES = 2;

    @Autowired
    private OverdueNotices notices;

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
        jdbc.update("delete from awards where user_id = ?", owner.getId());
        userRepository.delete(owner);
    }

    @Test
    void ac2_8_twoInstancesAtTheSameMinuteMarkAndAuditARequestOnce() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        long awardId = AwardRows.award(jdbc, owner.getId()).status("PENDING").insert();
        RequestRows.request(jdbc, awardId, owner.getId()).status("SUBMITTED").level("FACULTY_SECRETARY")
            .submittedAt(now.minus(5, ChronoUnit.DAYS)).deadline(now.minus(1, ChronoUnit.HOURS)).insert();
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> run = () -> {
            start.await();
            return notices.run(now);
        };
        ExecutorService pool = Executors.newFixedThreadPool(INSTANCES);
        try {
            List<Future<Integer>> runs = List.of(pool.submit(run), pool.submit(run));
            start.countDown();
            for (Future<Integer> future : runs) {
                future.get();
            }
        } finally {
            pool.shutdown();
        }

        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action_type = 'REVIEW_OVERDUE_NOTICED'"
            + " and entity_id = ?", Integer.class, awardId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select overdue_noticed_level from award_requests where award_id = ?",
            String.class, awardId)).isEqualTo("FACULTY_SECRETARY");
    }

    @Test
    void ac2_1_aRequestWithoutPassedDeadlineOrNotOpenIsNotMarked() {
        Instant now = Instant.now();
        long future = AwardRows.award(jdbc, owner.getId()).status("PENDING").insert();
        RequestRows.request(jdbc, future, owner.getId()).status("IN_REVIEW").level("DEAN")
            .submittedAt(now.minus(1, ChronoUnit.DAYS)).deadline(now.plus(1, ChronoUnit.DAYS)).insert();
        long returned = AwardRows.award(jdbc, owner.getId()).status("DRAFT").insert();
        RequestRows.request(jdbc, returned, owner.getId()).status("RETURNED").level("DEAN")
            .submittedAt(now.minus(9, ChronoUnit.DAYS)).deadline(now.minus(1, ChronoUnit.DAYS)).insert();

        notices.run(now);

        assertThat(jdbc.queryForObject("select count(*) from award_requests where award_id in (?, ?)"
            + " and overdue_noticed_at is not null", Integer.class, future, returned)).isZero();
    }
}
