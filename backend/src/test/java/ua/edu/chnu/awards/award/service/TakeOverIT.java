package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import ua.edu.chnu.awards.award.event.ReviewTakenOver;
import ua.edu.chnu.awards.award.repository.AwardRequestRepository;
import ua.edu.chnu.awards.notification.TakeOverMails;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.AwardRows;
import ua.edu.chnu.awards.support.RequestRows;
import ua.edu.chnu.awards.support.TestMails;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

class TakeOverIT extends AbstractIntegrationTest {

    private static final String OWNER = "it.takeover@chnu.edu.ua";
    private static final long MAIL_WAIT_MS = 30_000;
    private static final long QUIET_MS = 2_000;

    @MockitoSpyBean
    private JavaMailSender mailSender;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private StatusEstimator estimator;

    @Autowired
    private AwardRequestRepository requests;

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
        jdbc.update("delete from event_publication");
    }

    @Test
    void ac1_aRequestIsOverdueOnlyAfterItsStoredDeadline() {
        Instant now = Instant.now();

        assertThat(overdue(now.plus(1, ChronoUnit.MINUTES))).isFalse();
        assertThat(overdue(now.minus(1, ChronoUnit.SECONDS))).isTrue();
    }

    @Test
    void ac2_thePreviousHolderIsMailedOnceTheTakeOverCommits() {
        String to = "it.takeover.holder@chnu.edu.ua";

        transactions.executeWithoutResult(status -> events.publishEvent(takenOver(to)));

        verify(mailSender, timeout(MAIL_WAIT_MS)).send(ArgumentMatchers.<MimeMessage>argThat(
            message -> sentTo(to).matches(message) && TakeOverMails.SUBJECT.equals(TestMails.subject(message))));
    }

    @Test
    void ac2_aRolledBackTakeOverSendsNoMail() {
        String to = "it.takeover.rollback@chnu.edu.ua";

        transactions.executeWithoutResult(status -> {
            events.publishEvent(takenOver(to));
            status.setRollbackOnly();
        });

        verify(mailSender, after(QUIET_MS).never()).send(ArgumentMatchers.<MimeMessage>argThat(sentTo(to)));
    }

    private boolean overdue(Instant deadline) {
        long awardId = AwardRows.award(jdbc, owner.getId()).status("PENDING").insert();
        RequestRows.request(jdbc, awardId, owner.getId()).status("IN_REVIEW").level("FACULTY_SECRETARY")
            .submittedAt(deadline.minus(5, ChronoUnit.DAYS)).deadline(deadline).insert();
        return Objects.requireNonNull(transactions.execute(status -> requests.findByAwardId(awardId)
            .map(request -> estimator.timeline(request.getAward(), request).overdue()).orElseThrow()));
    }

    private static ReviewTakenOver takenOver(String to) {
        return new ReviewTakenOver(null, to, "Ірина", 1L, "Prize", "Премія", "Аліна Мартинюк", null);
    }

    private static ArgumentMatcher<MimeMessage> sentTo(String to) {
        return message -> message != null && TestMails.recipients(message).contains(to);
    }
}
