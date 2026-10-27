package ua.edu.chnu.awards.award.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import ua.edu.chnu.awards.award.dto.AwardForm;
import ua.edu.chnu.awards.award.repository.AwardRepository;
import ua.edu.chnu.awards.common.web.ClientRequest;
import ua.edu.chnu.awards.organization.entity.Organization;
import ua.edu.chnu.awards.organization.repository.OrganizationRepository;
import ua.edu.chnu.awards.support.AbstractIntegrationTest;
import ua.edu.chnu.awards.support.TestUsers;
import ua.edu.chnu.awards.user.entity.User;
import ua.edu.chnu.awards.user.repository.UserRepository;

class AwardCreationIT extends AbstractIntegrationTest {

    private static final String OWNER = "it.creation@chnu.edu.ua";
    private static final String OTHER = "it.creation.other@chnu.edu.ua";
    private static final String COUNT = "select count(*) from awards where user_id = ?";

    @Autowired
    private AwardCreation creation;

    @Autowired
    private AwardService awardService;

    @Autowired
    private AwardRepository awardRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    private User owner;
    private User other;

    @BeforeEach
    void setUp() {
        Organization department = organizationRepository.findById(TestUsers.DAI_DEPARTMENT_ID).orElseThrow();
        owner = userRepository.save(TestUsers.user(OWNER, department));
        other = userRepository.save(TestUsers.user(OTHER, department));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ClientRequest.CORRELATION_ATTRIBUTE, UUID.randomUUID());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        TestUsers.signInAs(owner);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        jdbc.update("delete from awards where user_id in (?, ?)", owner.getId(), other.getId());
        List.of(OWNER, OTHER).forEach(email ->
            userRepository.findByEmailAddressIgnoreCase(email).ifPresent(userRepository::delete));
    }

    @Test
    void ac4_1_aRepeatedCreationWithTheSameKeyStoresOneDraft() {
        UUID key = UUID.randomUUID();

        AwardCreation.Created first = creation.create(form("Letter"), key);
        AwardCreation.Created again = creation.create(form("Letter"), key);

        assertThat(first.repeated()).isFalse();
        assertThat(again.repeated()).isTrue();
        assertThat(again.award().id()).isEqualTo(first.award().id());
        assertThat(again.award().version()).isEqualTo(first.award().version());
        assertThat(jdbc.queryForObject(COUNT, Long.class, owner.getId())).isOne();
        assertThat(jdbc.queryForObject("select creation_key from awards where award_id = ?", UUID.class,
            first.award().id())).isEqualTo(key);
    }

    @Test
    void ac4_2_theRepeatAnswersTheDraftAsItIsNowAndKeepsItsValues() {
        UUID key = UUID.randomUUID();
        long id = creation.create(form("Letter"), key).award().id();
        awardService.update(id, new AwardForm("Diploma", null, null, null, null, null, null, null, null, 1L));

        AwardCreation.Created again = creation.create(form("Other tab"), key);

        assertThat(again.repeated()).isTrue();
        assertThat(again.award().title()).isEqualTo("Diploma");
        assertThat(again.award().version()).isEqualTo(2L);
        assertThat(jdbc.queryForObject(COUNT, Long.class, owner.getId())).isOne();
    }

    @Test
    void ac4_2_theUniqueIndexRefusesASecondDraftUnderOneKey() {
        UUID key = UUID.randomUUID();
        creation.create(form("Letter"), key);
        long second = awardService.create(form("Letter")).id();

        assertThatThrownBy(() -> transactions.executeWithoutResult(status ->
            awardRepository.keepCreationKey(second, key)))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(awardRepository.findIdByCreationKey(owner.getId(), key)).isPresent();
    }

    @Test
    void ac4_3_aKeyBelongsToItsOwnerAndCreationsWithoutAKeyStayIndependent() {
        creation.create(form("Letter"), null);
        creation.create(form("Letter"), null);
        UUID key = UUID.randomUUID();
        long mine = creation.create(form("Letter"), key).award().id();
        TestUsers.signInAs(other);

        AwardCreation.Created theirs = creation.create(form("Letter"), key);

        assertThat(theirs.repeated()).isFalse();
        assertThat(theirs.award().id()).isNotEqualTo(mine);
        assertThat(jdbc.queryForObject(COUNT, Long.class, owner.getId())).isEqualTo(3L);
        assertThat(jdbc.queryForObject(COUNT, Long.class, other.getId())).isOne();
    }

    private static AwardForm form(String title) {
        return new AwardForm(title, null, null, null, null, null, null, null, null, null);
    }
}
