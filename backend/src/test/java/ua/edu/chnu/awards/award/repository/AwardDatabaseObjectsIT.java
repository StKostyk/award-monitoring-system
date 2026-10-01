package ua.edu.chnu.awards.award.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.edu.chnu.awards.support.AwardRows.award;
import static ua.edu.chnu.awards.support.RequestRows.request;

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import ua.edu.chnu.awards.award.entity.RecognitionLevel;
import ua.edu.chnu.awards.support.AbstractJpaSliceTest;
import ua.edu.chnu.awards.support.TestUsers;

class AwardDatabaseObjectsIT extends AbstractJpaSliceTest {

    private static final long DMUS_DEPARTMENT_ID = 70L;
    private static final long FPPSW_FACULTY_ID = 10L;
    private static final String INSERT_USER = "insert into users (email_address, first_name, last_name, "
        + "password_hash, organization_id, account_status) values (?, 'Test', 'User', 'x', ?, ?) returning user_id";
    private static final String INSERT_ROLE = "insert into user_roles (user_id, role_type, organization_id, "
        + "valid_from) values (?, ?, ?, ?)";
    private static final String CAN_APPROVE = "select fn_can_user_approve_award(?, ?)";

    @Autowired
    private JdbcTemplate jdbc;

    private LocalDate today;
    private long owner;
    private long awardId;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(ZoneId.of("Europe/Kyiv"));
        owner = user("objects.owner@chnu.edu.ua", TestUsers.DAI_DEPARTMENT_ID, "ACTIVE");
        awardId = award(jdbc, owner).title("Objects award").status("PENDING").insert();
        request(jdbc, awardId, owner).status("SUBMITTED").level("DEAN").insert();
        jdbc.update("update users set organization_id = ? where user_id = ?", DMUS_DEPARTMENT_ID, owner);
    }

    @Test
    void activeAwardsShowTheOrganisationOfTheSubmissionAfterTheOwnerMoves() {
        jdbc.update("update awards set status = 'APPROVED' where award_id = ?", awardId);

        assertThat(jdbc.queryForObject("select organization_id from vw_active_awards where award_id = ?",
            Long.class, awardId)).isEqualTo(TestUsers.DAI_DEPARTMENT_ID);
    }

    @Test
    void pendingRequestsShowTheOrganisationOfTheSubmissionAfterTheOwnerMoves() {
        assertThat(jdbc.queryForObject("select organization_name from vw_pending_requests where award_id = ?",
            String.class, awardId)).isEqualTo(organisationName(TestUsers.DAI_DEPARTMENT_ID));
    }

    @Test
    void statisticsCountAnAwardForTheOrganisationOfTheSubmission() {
        long before = pendingIn(TestUsers.DAI_DEPARTMENT_ID);
        award(jdbc, owner).title("Second").status("PENDING").insert();

        assertThat(pendingIn(TestUsers.DAI_DEPARTMENT_ID)).isEqualTo(before + 1);
        assertThat(pendingIn(DMUS_DEPARTMENT_ID)).isZero();
    }

    @ParameterizedTest
    @EnumSource(RecognitionLevel.class)
    void theImpactScoreFunctionUsesTheBaseScoreOfTheApplication(RecognitionLevel level) {
        Long category = jdbc.queryForObject("insert into award_categories (name, level) values (?, ?) "
            + "returning category_id", Long.class, "Objects " + level, level.name());

        assertThat(jdbc.queryForObject("select fn_calculate_impact_score(?)", Integer.class, category))
            .isEqualTo(level.baseScore());
    }

    @Test
    void aDeanOfTheSubmissionFacultyMayApproveAfterTheOwnerMoves() {
        long dean = user("objects.dean@chnu.edu.ua", TestUsers.FMI_FACULTY_ID, "ACTIVE");
        jdbc.update(INSERT_ROLE, dean, "DEAN", TestUsers.FMI_FACULTY_ID, today.minusDays(1));
        long otherDean = user("objects.other.dean@chnu.edu.ua", FPPSW_FACULTY_ID, "ACTIVE");
        jdbc.update(INSERT_ROLE, otherDean, "DEAN", FPPSW_FACULTY_ID, today.minusDays(1));

        assertThat(canApprove(dean)).isTrue();
        assertThat(canApprove(otherDean)).isFalse();
    }

    @Test
    void aDelegateInEffectMayApproveAndARevokedOneMayNot() {
        long dean = user("objects.dean@chnu.edu.ua", TestUsers.FMI_FACULTY_ID, "ACTIVE");
        jdbc.update(INSERT_ROLE, dean, "DEAN", TestUsers.FMI_FACULTY_ID, today.minusDays(1));
        long delegate = user("objects.delegate@chnu.edu.ua", TestUsers.FMI_FACULTY_ID, "ACTIVE");
        long delegation = jdbc.queryForObject("insert into role_delegations (delegator_id, delegate_id, role_type, "
            + "organization_id, valid_from, valid_to) values (?, ?, 'DEAN', ?, ?, ?) returning delegation_id",
            Long.class, dean, delegate, TestUsers.FMI_FACULTY_ID, today, today.plusDays(3));

        assertThat(canApprove(delegate)).isTrue();

        jdbc.update("update role_delegations set revoked_at = now(), revoked_by = ? where delegation_id = ?", dean,
            delegation);
        assertThat(canApprove(delegate)).isFalse();
    }

    @Test
    void aDelegationStopsCountingWhenTheDelegatorLosesTheRole() {
        long dean = user("objects.dean@chnu.edu.ua", TestUsers.FMI_FACULTY_ID, "ACTIVE");
        jdbc.update(INSERT_ROLE, dean, "DEAN", TestUsers.FMI_FACULTY_ID, today.minusDays(5));
        long delegate = user("objects.delegate@chnu.edu.ua", TestUsers.FMI_FACULTY_ID, "ACTIVE");
        jdbc.update("insert into role_delegations (delegator_id, delegate_id, role_type, organization_id, valid_from, "
            + "valid_to) values (?, ?, 'DEAN', ?, ?, ?)", dean, delegate, TestUsers.FMI_FACULTY_ID, today,
            today.plusDays(3));

        jdbc.update("update user_roles set valid_to = ? where user_id = ?", today.minusDays(1), dean);

        assertThat(canApprove(delegate)).isFalse();
    }

    @Test
    void aRetiredHolderAndADelegateOfAUniversityRoleMayApprove() {
        jdbc.update("update award_requests set current_level = 'RECTOR' where award_id = ?", awardId);
        long rector = user("objects.rector@chnu.edu.ua", TestUsers.UNIVERSITY_ID, "RETIRED");
        jdbc.update(INSERT_ROLE, rector, "RECTOR", TestUsers.UNIVERSITY_ID, today.minusDays(1));
        long delegate = user("objects.delegate@chnu.edu.ua", TestUsers.FMI_FACULTY_ID, "ACTIVE");
        jdbc.update("insert into role_delegations (delegator_id, delegate_id, role_type, organization_id, valid_from, "
            + "valid_to) values (?, ?, 'RECTOR', ?, ?, ?)", rector, delegate, TestUsers.UNIVERSITY_ID, today,
            today.plusDays(3));

        assertThat(canApprove(rector)).isTrue();
        assertThat(canApprove(delegate)).isTrue();
    }

    @Test
    void neitherTheOwnerNorAHolderWhoCannotSignInMayApprove() {
        jdbc.update(INSERT_ROLE, owner, "DEAN", TestUsers.FMI_FACULTY_ID, today.minusDays(1));
        long suspended = user("objects.suspended@chnu.edu.ua", TestUsers.FMI_FACULTY_ID, "SUSPENDED");
        jdbc.update(INSERT_ROLE, suspended, "DEAN", TestUsers.FMI_FACULTY_ID, today.minusDays(1));

        assertThat(canApprove(owner)).isFalse();
        assertThat(canApprove(suspended)).isFalse();
    }

    @Test
    void nobodyMayApproveAClosedRequest() {
        long dean = user("objects.dean@chnu.edu.ua", TestUsers.FMI_FACULTY_ID, "ACTIVE");
        jdbc.update(INSERT_ROLE, dean, "DEAN", TestUsers.FMI_FACULTY_ID, today.minusDays(1));
        jdbc.update("update award_requests set status = 'RETURNED' where award_id = ?", awardId);

        assertThat(canApprove(dean)).isFalse();
    }

    private long user(String email, long organizationId, String status) {
        return jdbc.queryForObject(INSERT_USER, Long.class, email, organizationId, status);
    }

    private boolean canApprove(long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(CAN_APPROVE, Boolean.class, userId, awardId));
    }

    private long pendingIn(long organizationId) {
        return jdbc.queryForObject("select pending_awards from vw_award_statistics where org_id = ?", Long.class,
            organizationId);
    }

    private String organisationName(long organizationId) {
        return jdbc.queryForObject("select name from organizations where org_id = ?", String.class, organizationId);
    }
}
