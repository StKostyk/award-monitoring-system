package ua.edu.chnu.awards.award.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ua.edu.chnu.awards.support.AwardRows.award;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import ua.edu.chnu.awards.award.dto.AwardQuery;
import ua.edu.chnu.awards.award.entity.Award;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.support.AbstractJpaSliceTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;

class AwardSchemaIT extends AbstractJpaSliceTest {

    private static final String KYIV_TODAY = "(now() at time zone 'Europe/Kyiv')::date";

    @Autowired
    private AwardRepository awards;

    @Autowired
    private AwardRequestRepository requests;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private User owner;
    private Organization department;

    @BeforeEach
    void setUp() {
        department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        owner = userRepository.saveAndFlush(TestUsers.user("schema.owner@chnu.edu.ua", department));
    }

    @Test
    void ac1_1_aDraftWithOnlyAUkrainianTitleIsStored() {
        Award draft = awards.saveAndFlush(Award.builder().owner(owner).organization(department)
            .titleUk("Подяка").build());

        assertThat(draft.getVersion()).isNotNull();
        assertThat(jdbc.queryForObject("select organization_id from awards where award_id = ?", Long.class,
            draft.getId())).isEqualTo(64L);
    }

    @Test
    void ac1_1_anAwardWithoutAnyTitleIsRefused() {
        assertThatThrownBy(() -> award(jdbc, owner.getId()).title(null).category(null).awardingOrganization(null)
            .awardDate(null).insert())
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_awards_title");
    }

    @Test
    void edge_theDatabaseRefusesAnIncompleteAwardOutsideDraft() {
        assertThatThrownBy(() -> award(jdbc, owner.getId()).status("PENDING").awardingOrganization(null).insert())
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_awards_complete");
    }

    @Test
    void ac2_1_todayOnTheKyivCalendarIsAcceptedWhateverTheSessionZone() {
        jdbc.execute("set local time zone 'UTC'");

        assertThat(jdbc.update("insert into awards (user_id, organization_id, title, award_date) values (?, 64, "
            + "'Letter', " + KYIV_TODAY + ")", owner.getId())).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("insert into awards (user_id, organization_id, title, award_date) "
            + "values (?, 64, 'Letter', " + KYIV_TODAY + " + 1)", owner.getId()))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_awards_date");
    }

    @Test
    void ac1_6_anAwardHasOneRequestAtMost() {
        long awardId = award(jdbc, owner.getId()).status("PENDING").insert();
        String request = "insert into award_requests (award_id, submitter_id, current_level) "
            + "values (?, ?, 'FACULTY_SECRETARY')";
        jdbc.update(request, awardId, owner.getId());

        assertThat(requests.findByAwardId(awardId)).isPresent();
        assertThat(requests.findByAwardIdIn(List.of(awardId))).hasSize(1);
        assertThatThrownBy(() -> jdbc.update(request, awardId, owner.getId()))
            .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uk_award_requests_award");
    }

    @Test
    void ac1_7_theOwnListFiltersByStatusCategoryAndDate() {
        award(jdbc, owner.getId()).title("Draft").category(null).awardingOrganization(null).awardDate(null).insert();
        award(jdbc, owner.getId()).title("Old").status("PENDING").awardDate(LocalDate.of(2020, 1, 1)).insert();
        award(jdbc, owner.getId()).title("New").status("PENDING").insert();
        User other = userRepository.saveAndFlush(TestUsers.user("schema.other@chnu.edu.ua", department));
        award(jdbc, other.getId()).title("Theirs").status("PENDING").insert();
        AwardSpecifications specifications = new AwardSpecifications();

        assertThat(awards.findAll(specifications.ownedBy(owner.getId(), new AwardQuery(null, null, null, null)),
            PageRequest.of(0, 10)).getContent()).extracting(Award::getTitle)
            .containsExactlyInAnyOrder("Draft", "Old", "New");
        assertThat(awards.findAll(specifications.ownedBy(owner.getId(), new AwardQuery(AwardStatus.PENDING, 13L,
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31))), PageRequest.of(0, 10)).getContent())
            .extracting(Award::getTitle).containsExactly("New");
    }

    @Test
    void ac1_6_theDraftIsReadUnderARowLock() {
        Award draft = awards.saveAndFlush(Award.builder().owner(owner).organization(department).title("Letter")
            .build());

        assertThat(awards.findForUpdate(draft.getId())).get().extracting(Award::getTitle).isEqualTo("Letter");
        assertThat(awards.findWithDetailsById(draft.getId())).get()
            .extracting(award -> award.getOrganization().getId()).isEqualTo(64L);
    }
}
