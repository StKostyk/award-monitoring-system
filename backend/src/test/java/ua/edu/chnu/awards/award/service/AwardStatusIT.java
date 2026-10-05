package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Supplier;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import ua.edu.chnu.awards.award.dto.AwardQuery;
import ua.edu.chnu.awards.award.dto.AwardResponse;
import ua.edu.chnu.awards.award.dto.AwardStatusView;
import ua.edu.chnu.awards.award.dto.DecisionView;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.AwardRows;
import ua.edu.chnu.awards.support.DecisionRows;
import ua.edu.chnu.awards.support.RequestRows;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;

class AwardStatusIT extends AbstractIntegrationTest {

    private static final String OWNER = "it.status@chnu.edu.ua";
    private static final String SECRETARY = "it.status.secretary@chnu.edu.ua";
    private static final String DEAN = "it.status.dean@chnu.edu.ua";

    @Autowired
    private AwardStatusService statusService;

    @Autowired
    private AwardService awardService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbc;

    private User owner;
    private User secretary;
    private User dean;
    private Statistics statistics;

    @BeforeEach
    void setUp() {
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        owner = userRepository.save(TestUsers.user(OWNER, department));
        secretary = userRepository.save(TestUsers.user(SECRETARY, department));
        dean = userRepository.save(TestUsers.user(DEAN, department));
        TestUsers.signInAs(owner);
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    @AfterEach
    void tearDown() {
        statistics.setStatisticsEnabled(false);
        SecurityContextHolder.clearContext();
        jdbc.update("delete from awards where user_id = ?", owner.getId());
        List.of(OWNER, SECRETARY, DEAN).forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @Test
    void ac1_6_decisionsAreReadWithTheirReviewersInOneQueryOldestFirst() {
        final long quiet = submitted("Quiet");
        long reviewed = submitted("Reviewed");
        long request = RequestRows.idOf(jdbc, reviewed);
        Instant start = Instant.now().minus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        decide(request, secretary, "RETURNED", "FACULTY_SECRETARY", "Додайте номер наказу", start);
        decide(request, secretary, "APPROVED", "FACULTY_SECRETARY", null, start.plus(1, ChronoUnit.DAYS));
        decide(request, dean, "RETURNED", "DEAN", "Уточніть дату", start.plus(2, ChronoUnit.DAYS));

        long withoutDecisions = statements(() -> statusService.status(quiet));
        AwardStatusView view = statusService.status(reviewed);
        long withDecisions = statements(() -> statusService.status(reviewed));

        assertThat(withDecisions).isEqualTo(withoutDecisions);
        assertThat(view.decisions()).extracting(DecisionView::comments)
            .containsExactly("Додайте номер наказу", null, "Уточніть дату");
        assertThat(view.decisions()).extracting(DecisionView::reviewerName).containsOnly("Test User");
        assertThat(view.decisions()).extracting(DecisionView::reviewerId)
            .containsExactly(secretary.getId(), secretary.getId(), dean.getId());
    }

    @Test
    void ac1_10_ac1_11_theListAddsNoQueryPerRowForTheRequestFields() {
        submitted("First");
        submitted("Second");
        AwardQuery pending = new AwardQuery(AwardStatus.PENDING, null, null, null);
        long two = statements(() -> awardService.listOwn(pending, 0, 20));
        for (int i = 0; i < 6; i++) {
            submitted("More " + i);
        }

        List<AwardResponse> eight = awardService.listOwn(pending, 0, 20).getContent();
        long many = statements(() -> awardService.listOwn(pending, 0, 20));

        assertThat(many).isEqualTo(two);
        assertThat(eight).hasSize(8).allSatisfy(award -> {
            assertThat(award.request().deadline()).isNotNull();
            assertThat(award.request().estimatedCompletion()).isNotNull();
            assertThat(award.request().overdue()).isFalse();
        });
    }

    private long statements(Supplier<?> call) {
        statistics.clear();
        call.get();
        return statistics.getPrepareStatementCount();
    }

    private long submitted(String title) {
        long id = AwardRows.award(jdbc, owner.getId()).title(title).status("PENDING").insert();
        RequestRows.request(jdbc, id, owner.getId()).insert();
        return id;
    }

    private void decide(long request, User reviewer, String decision, String level, String comments, Instant at) {
        DecisionRows.decision(jdbc, request, reviewer.getId()).type(decision).level(level).comments(comments)
            .decidedAt(at).insert();
    }
}
