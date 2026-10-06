package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.edu.chnu.awards.support.AwardRows.award;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import ua.edu.chnu.awards.award.dto.DuplicateMatch;
import ua.edu.chnu.awards.award.entity.AwardStatus;
import ua.edu.chnu.awards.support.AbstractJpaSliceTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.Organization;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.OrganizationRepository;
import ua.edu.chnu.awards.user.repository.UserRepository;

class DuplicateFinderIT extends AbstractJpaSliceTest {

    private static final LocalDate DATE = LocalDate.of(2025, 5, 1);
    private static final String TITLE = "Грамота Міністерства освіти і науки";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    private DuplicateFinder finder;
    private User owner;

    @BeforeEach
    void setUp() {
        finder = new DuplicateFinder(new NamedParameterJdbcTemplate(jdbc));
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        owner = userRepository.saveAndFlush(TestUsers.user("duplicate.owner@chnu.edu.ua", department));
    }

    @Test
    void ac2_4_aSimilarUkrainianTitleOnTheSameDayIsAMatch() {
        long first = insert(owner, null, "Грамота Міністерства освіти і науки", "PENDING", DATE);
        long second = insert(owner, null, "грамота міністерства освіти і науки України", "DRAFT", DATE);

        assertThat(finder.matches(List.of(second))).containsExactly(Map.entry(second,
            List.of(new DuplicateMatch(first, null, "Грамота Міністерства освіти і науки", DATE,
                AwardStatus.PENDING))));
    }

    @Test
    void ac2_4_englishTitlesAreComparedIgnoringCase() {
        long first = insert(owner, "Best Paper Award", null, "DRAFT", DATE);
        long second = insert(owner, "best paper award", "Нагорода за найкращу статтю", "DRAFT", DATE);

        assertThat(finder.matches(List.of(first, second))).containsOnlyKeys(first, second);
    }

    @Test
    void ac2_4_anotherDayADifferentTitleOrAnotherOwnerIsNoMatch() {
        User colleague = userRepository.saveAndFlush(TestUsers.user("duplicate.colleague@chnu.edu.ua",
            owner.getOrganization()));
        insert(owner, null, "Грамота Міністерства освіти і науки", "PENDING", DATE.minusDays(1));
        insert(owner, null, "Подяка ректора університету", "PENDING", DATE);
        insert(colleague, null, "Грамота Міністерства освіти і науки", "PENDING", DATE);
        long draft = insert(owner, null, "Грамота Міністерства освіти і науки", "DRAFT", DATE);

        assertThat(finder.matches(List.of(draft))).isEmpty();
        assertThat(finder.matches(List.of())).isEmpty();
    }

    @Test
    void ac0_5_unitAwardsAreComparedWithTheAwardsOfTheSameUnitWhoeverEnteredThem() {
        User secretary = userRepository.saveAndFlush(TestUsers.user("duplicate.secretary@chnu.edu.ua",
            owner.getOrganization()));
        long first = award(jdbc, owner.getId()).unit(TestUsers.FMI_FACULTY_ID).titleUk(TITLE).status("PENDING")
            .insert();
        long draft = award(jdbc, secretary.getId()).unit(TestUsers.FMI_FACULTY_ID).titleUk(TITLE).insert();
        award(jdbc, secretary.getId()).unit(TestUsers.DAI_DEPARTMENT_ID).titleUk(TITLE).insert();
        award(jdbc, owner.getId()).unit(TestUsers.FMI_FACULTY_ID).titleUk(TITLE).insert();

        assertThat(finder.matches(List.of(draft)).get(draft)).extracting(DuplicateMatch::id).containsExactly(first);
    }

    @Test
    void ac0_5_personalAndUnitAwardsAreNeverCompared() {
        long personal = insert(owner, null, TITLE, "PENDING", DATE);
        long unit = award(jdbc, owner.getId()).unit(TestUsers.DAI_DEPARTMENT_ID).titleUk(TITLE).insert();

        assertThat(finder.matches(List.of(personal, unit))).isEmpty();
    }

    private long insert(User user, String title, String titleUk, String status, LocalDate date) {
        return award(jdbc, user.getId()).title(title).titleUk(titleUk).status(status).awardDate(date).insert();
    }
}
